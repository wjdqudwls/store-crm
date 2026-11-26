package com.crmpos.sales.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.customer.api.CustomerApi;
import com.crmpos.customer.api.SaleCustomer;
import com.crmpos.sales.controller.ExchangeRequest;
import com.crmpos.sales.controller.ExchangeResponse;
import com.crmpos.sales.controller.SaleRequest;
import com.crmpos.sales.domain.SaleLine;
import com.crmpos.sales.repository.SaleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 교환 (SAL-06). 매장 계정이 자기 매장의 판매만 한다. 트랜잭션 밖에서 권한·입력을 확인하고 새 상품 라인을 만든 뒤
 * {@link ExchangeWriter}가 한 트랜잭션으로 저장한다. 새 상품 라인 규칙은 판매 기록과 같고, 고객은 원 판매의 고객이다.
 * 같은 키는 저장된 교환으로 응답한다 (ADR-006). 요청 본문은 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRecordingService {

    /** 처음 저장했으면 created=true(201), 같은 키의 다시 보낸 요청이면 false(200). */
    public record Result(ExchangeResponse response, boolean created) {
    }

    private final ExchangeWriter writer;
    private final SaleRecordingService recordingService;
    private final SaleRepository saleRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerApi customerApi;
    private final ObjectMapper objectMapper;

    public Result exchange(Long saleId, String idempotencyKey, ExchangeRequest request, CurrentAccount account) {
        if (account.role() != StaffRole.STORE || account.storeId() == null) {
            throw new BusinessException(ErrorCode.STORE_REQUIRED);
        }
        String key = SalesInputRules.requireKey(idempotencyKey);
        Long saleStoreId = saleRepository.findStoreId(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));
        if (!saleStoreId.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        String requestHash = requestHash(saleId, request, account.storeId());

        // 이미 저장한 요청이면 입력을 다시 검사하지 않는다
        var stored = writer.findByKey(key);
        if (stored.isPresent()) {
            return toResult(stored.get(), requestHash);
        }
        if (saleRepository.findByIdempotencyKey(key).isPresent()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        Long customerId = saleRepository.findCustomerId(saleId).orElse(null);
        SaleRequest newSale = new SaleRequest(request.staffId(), customerId, request.newLines(), null, null);
        List<FieldError> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.returnLines().size(); i++) {
            if (!seen.add(request.returnLines().get(i).lineId())) {
                errors.add(new FieldError("returnLines[" + i + "].lineId", "같은 줄을 두 번 고를 수 없습니다."));
            }
        }
        // 새 상품 라인의 오류 칸 이름은 요청의 newLines로 바꾼다
        List<FieldError> lineErrors = new ArrayList<>();
        SaleRecordingService.checkLineShapes(newSale, lineErrors);
        lineErrors.forEach(f -> errors.add(new FieldError(f.field().replaceFirst("^lines", "newLines"), f.reason())));
        SalesInputRules.checkStoreStaff(staffRepository, request.staffId(), account.storeId(), "staffId", errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        SaleRecordingService.checkDirectSizes(newSale);

        Store store = storeRepository.findById(account.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        SaleCustomer customer = customerId == null ? null : customerApi.findForSale(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        List<SaleLine> lines = recordingService.buildLines(newSale, customer);
        NewExchange exchange = new NewExchange(saleId, account.storeId(), store.getCode(), key, requestHash,
                request.staffId(), account.id(), request.returnLines(), lines);
        try {
            return toResult(writer.exchange(exchange), requestHash);
        } catch (DataIntegrityViolationException e) {
            return writer.findByKey(key).map(s -> toResult(s, requestHash)).orElseThrow(() -> e);
        }
    }

    private static Result toResult(ExchangeWriter.Stored stored, String requestHash) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return new Result(stored.response(), stored.created());
    }

    /** 요청 해시: 종류·판매·본문·매장. 같은 키를 다른 판매·다른 내용에 쓰면 409다 (ADR-006). */
    private String requestHash(Long saleId, ExchangeRequest request, Long storeId) {
        try {
            String text = "exchange|" + saleId + "|" + objectMapper.writeValueAsString(request) + "|" + storeId;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.");
        }
    }
}
