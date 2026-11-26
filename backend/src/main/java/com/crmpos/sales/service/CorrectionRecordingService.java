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
import com.crmpos.sales.controller.CorrectionRequest;
import com.crmpos.sales.controller.CorrectionResponse;
import com.crmpos.sales.controller.SaleRequest;
import com.crmpos.sales.domain.SaleLine;
import com.crmpos.sales.repository.SaleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 판매 정정 (SAL-07, BR-16). 매장 계정(자기 매장·판매한 날·마감 전)과 본사(언제나)만 한다. 트랜잭션 밖에서 권한·입력을
 * 확인하고 라인·고객을 만든 뒤 {@link CorrectionWriter}가 한 트랜잭션으로 저장한다. 라인 규칙은 판매 기록과 같고
 * ({@link SaleRecordingService}), 같은 키는 저장된 정정으로 응답한다 (ADR-006). 요청 본문은 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CorrectionRecordingService {

    /** 처음 저장했으면 created=true(201), 같은 키의 다시 보낸 요청이면 false(200). */
    public record Result(CorrectionResponse sale, boolean created) {
    }

    private final CorrectionWriter writer;
    private final SaleRecordingService recordingService;
    private final SaleRepository saleRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerApi customerApi;
    private final ObjectMapper objectMapper;

    public Result correct(Long saleId, String idempotencyKey, CorrectionRequest request, CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        String key = SalesInputRules.requireKey(idempotencyKey);
        // 권한을 입력 검사보다 먼저: 없는 판매는 404, 매장 계정이 다른 매장의 판매는 403
        Long saleStoreId = saleRepository.findStoreId(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));
        if (account.role() == StaffRole.STORE && !saleStoreId.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        String requestHash = requestHash(saleId, request, account);

        // 이미 저장한 요청이면 입력을 다시 검사하지 않는다 (그 사이 직원·상품이 바뀌어도 같은 응답)
        var stored = writer.findByKey(key);
        if (stored.isPresent()) {
            return toResult(stored.get(), requestHash);
        }
        if (saleRepository.findByIdempotencyKey(key).isPresent()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        SaleRequest lineRequest = new SaleRequest(null, request.customerId(), request.lines(), null, null);
        List<FieldError> errors = new ArrayList<>();
        SaleRecordingService.checkLineShapes(lineRequest, errors);
        Long staffId = account.id();
        if (account.role() == StaffRole.STORE) {
            staffId = request.staffId();
            if (staffId == null) {
                errors.add(new FieldError("staffId", "처리한 직원을 골라 주세요."));
            } else {
                SalesInputRules.checkStoreStaff(staffRepository, staffId, account.storeId(), "staffId", errors);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        SaleRecordingService.checkDirectSizes(lineRequest);

        Store store = storeRepository.findById(saleStoreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        SaleCustomer customer = null;
        if (request.customerId() != null) {
            customer = customerApi.findForSale(request.customerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        }
        List<SaleLine> lines = recordingService.buildLines(lineRequest, customer);
        NewCorrection correction = new NewCorrection(saleId, account.role(), account.storeId(), store.getCode(), key,
                requestHash, staffId, account.id(), customer == null ? null : customer.id(),
                customer == null ? null : customer.currentGrade(), lines);
        try {
            return toResult(writer.correct(correction), requestHash);
        } catch (DataIntegrityViolationException e) {
            return writer.findByKey(key).map(s -> toResult(s, requestHash)).orElseThrow(() -> e);
        }
    }

    private static Result toResult(CorrectionWriter.Stored stored, String requestHash) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return new Result(stored.response(), stored.created());
    }

    /** 요청 해시: 종류·판매·본문·역할·매장. 같은 키를 다른 판매·다른 내용에 쓰면 409다 (ADR-006). */
    private String requestHash(Long saleId, CorrectionRequest request, CurrentAccount account) {
        try {
            String text = "correction|" + saleId + "|" + objectMapper.writeValueAsString(request) + "|"
                    + account.role() + "|" + account.storeId();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.");
        }
    }
}
