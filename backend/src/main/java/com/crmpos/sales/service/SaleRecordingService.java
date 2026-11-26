package com.crmpos.sales.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.api.CustomerApi;
import com.crmpos.customer.api.SaleCustomer;
import com.crmpos.product.api.ProductApi;
import com.crmpos.product.api.SkuInfo;
import com.crmpos.sales.controller.SaleRequest;
import com.crmpos.sales.controller.SaleResponse;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 판매 기록 (SAL-01, BR-10·12). 이 메서드는 트랜잭션 밖이다: 읽기·검증을 끝낸 뒤 {@link SaleWriter}가 한 트랜잭션으로 저장하고,
 * 같은 키가 동시에 와서 UNIQUE에 걸리면 저장된 판매를 읽어 응답한다 (ADR-006).
 * 요청의 메모·ID는 로그에 남기지 않는다 (NFR-04b).
 */
@Service
public class SaleRecordingService {

    /** 처음 저장했으면 created=true(201), 같은 키의 다시 보낸 요청이면 false(200). */
    public record Result(SaleResponse sale, boolean created) {
    }

    /** 화면 시계와 서버 시계의 차이를 받아 주는 시간 (미래 시각 거절 기준). */
    private static final long CLOCK_SKEW_SECONDS = 60;

    private final SaleWriter saleWriter;
    private final SaleReplayReader replayReader;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final ProductApi productApi;
    private final CustomerApi customerApi;
    private final ObjectMapper objectMapper;
    private final boolean allowPastSoldAt;

    public SaleRecordingService(SaleWriter saleWriter, SaleReplayReader replayReader, StaffRepository staffRepository,
            StoreRepository storeRepository, ProductApi productApi, CustomerApi customerApi,
            ObjectMapper objectMapper, @Value("${crm.sales.allow-past-sold-at:false}") boolean allowPastSoldAt) {
        this.saleWriter = saleWriter;
        this.replayReader = replayReader;
        this.staffRepository = staffRepository;
        this.storeRepository = storeRepository;
        this.productApi = productApi;
        this.customerApi = customerApi;
        this.objectMapper = objectMapper;
        this.allowPastSoldAt = allowPastSoldAt;
    }

    public Result record(String idempotencyKey, SaleRequest request, CurrentAccount account) {
        if (account.role() != StaffRole.STORE || account.storeId() == null) {
            throw new BusinessException(ErrorCode.STORE_REQUIRED);
        }
        String key = SalesInputRules.requireKey(idempotencyKey);
        String requestHash = requestHash(request, account.storeId());

        Result replay = replayIfStored(key, requestHash);
        if (replay != null) {
            return replay;
        }

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        List<FieldError> errors = new ArrayList<>();
        LocalDateTime soldAt = resolveSoldAt(request.soldAt(), now, allowPastSoldAt, errors);
        checkLineShapes(request, errors);
        if (request.customerId() == null && request.memo() != null && !request.memo().isBlank()) {
            errors.add(new FieldError("memo", "비회원 판매에는 메모를 남길 수 없습니다."));
        }
        Staff seller = SalesInputRules.checkStoreStaff(staffRepository, request.sellerStaffId(), account.storeId(),
                "sellerStaffId", errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        checkDirectSizes(request);

        Store store = storeRepository.findById(account.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        SaleCustomer customer = null;
        if (request.customerId() != null) {
            customer = customerApi.findForSale(request.customerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        }
        List<SaleLine> lines = buildLines(request, customer);
        NewSale newSale = new NewSale(store.getId(), store.getCode(), key, requestHash, seller.getId(), account.id(),
                customer == null ? null : customer.id(), customer == null ? null : customer.currentGrade(), soldAt,
                blankToNull(request.memo()), lines);

        try {
            Sale saved = saleWriter.insert(newSale);
            return new Result(SaleResponse.from(saved), true);
        } catch (DataIntegrityViolationException e) {
            // 같은 키가 동시에 와서 UNIQUE가 하나만 통과시킨 경우: 먼저 저장된 판매로 응답한다
            Result stored = replayIfStored(key, requestHash);
            if (stored != null) {
                return stored;
            }
            throw e;
        }
    }

    private Result replayIfStored(String key, String requestHash) {
        return replayReader.findByKey(key).map(stored -> {
            if (!stored.requestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return new Result(stored.response(), false);
        }).orElse(null);
    }

    /**
     * 판매 시각: 생략하면 지금, 오늘(한국 시간) 0시 이후부터 지금까지만 받는다. 과거 날짜는 설정(seed 프로필)이 켜졌을 때만.
     * 미래 시각은 어떤 경우에도 거절한다.
     */
    static LocalDateTime resolveSoldAt(OffsetDateTime requested, LocalDateTime now, boolean allowPast,
            List<FieldError> errors) {
        // DB(DATETIME(6))는 마이크로초까지만 저장한다. 나노초를 남기면 처음 응답과 DB에서 다시 읽은 응답이 달라진다.
        LocalDateTime exactNow = now.truncatedTo(ChronoUnit.MICROS);
        if (requested == null) {
            return exactNow;
        }
        LocalDateTime soldAt = KstTime.toLocal(requested).truncatedTo(ChronoUnit.MICROS);
        boolean future = soldAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS));
        boolean beforeToday = soldAt.isBefore(now.toLocalDate().atStartOfDay());
        if (future || (beforeToday && !allowPast)) {
            errors.add(new FieldError("soldAt", "판매 시각은 오늘 0시부터 지금까지만 고를 수 있습니다."));
            return exactNow;
        }
        return soldAt.isAfter(exactNow) ? exactNow : soldAt;
    }

    static void checkLineShapes(SaleRequest request, List<FieldError> errors) {
        List<SaleRequest.Line> lines = request.lines();
        for (int i = 0; i < lines.size(); i++) {
            SaleRequest.Line line = lines.get(i);
            String at = "lines[" + i + "]";
            boolean hasDirect = notBlank(line.productName()) || line.itemType() != null || notBlank(line.size())
                    || line.target() != null || notBlank(line.color());
            if (line.skuId() != null && hasDirect) {
                errors.add(new FieldError(at, "등록 상품과 직접 입력을 같이 쓸 수 없습니다."));
            } else if (line.skuId() == null) {
                if (!notBlank(line.productName())) {
                    errors.add(new FieldError(at + ".productName", "상품명이 필요합니다."));
                }
                if (line.itemType() == null) {
                    errors.add(new FieldError(at + ".itemType", "품목이 필요합니다."));
                }
                if (!notBlank(line.size())) {
                    errors.add(new FieldError(at + ".size", "사이즈가 필요합니다."));
                }
            }
            if (line.memberId() != null && request.customerId() == null) {
                errors.add(new FieldError(at + ".memberId", "비회원 판매에는 착용자를 고를 수 없습니다."));
            }
        }
    }

    /** 직접 입력한 사이즈는 품목·대상의 허용값이어야 한다 (02-erd.md 3.2). */
    static void checkDirectSizes(SaleRequest request) {
        List<String> invalid = new ArrayList<>();
        for (SaleRequest.Line line : request.lines()) {
            if (line.skuId() == null && !SizeRule.isAllowed(line.itemType(), line.target(), line.size().trim())) {
                invalid.add(line.size().trim());
            }
        }
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_SIZE, Map.of("invalidSizes", invalid));
        }
    }

    List<SaleLine> buildLines(SaleRequest request, SaleCustomer customer) {
        Set<Long> skuIds = new HashSet<>();
        Set<Long> memberIds = new HashSet<>();
        for (SaleRequest.Line line : request.lines()) {
            if (line.skuId() != null) {
                skuIds.add(line.skuId());
            }
            if (line.memberId() != null) {
                memberIds.add(line.memberId());
            }
        }
        Map<Long, SkuInfo> skus = productApi.findSkus(skuIds);
        Map<Long, String> members = customer == null ? Map.of()
                : customerApi.findMemberNicknames(customer.id(), memberIds);

        List<SaleLine> lines = new ArrayList<>();
        for (SaleRequest.Line line : request.lines()) {
            String wearerLabel = null;
            if (line.memberId() != null) {
                wearerLabel = members.get(line.memberId());
                if (wearerLabel == null) {
                    throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
                }
            }
            if (line.skuId() != null) {
                SkuInfo sku = skus.get(line.skuId());
                if (sku == null) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                }
                lines.add(SaleLine.of(sku.skuId(), sku.productCode(), sku.productName(), sku.target(), sku.itemType(),
                        sku.color() == null ? "" : sku.color(), sku.size(), sku.listPrice(), line.qty(),
                        line.lineAmount(), line.memberId(), wearerLabel));
            } else {
                lines.add(SaleLine.of(null, null, line.productName().trim(), line.target(), line.itemType(),
                        blankToNull(line.color()), line.size().trim(), null, line.qty(), line.lineAmount(),
                        line.memberId(), wearerLabel));
            }
        }
        return lines;
    }

    /** 요청 본문과 매장으로 SHA-256을 만든다. 같은 키로 다른 내용이 오면 해시가 달라 거절한다 (ADR-006). */
    String requestHash(SaleRequest request, Long storeId) {
        try {
            String text = objectMapper.writeValueAsString(request) + "|" + storeId;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
