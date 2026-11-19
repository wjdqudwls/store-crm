package com.crmpos.sales.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.controller.ReturnRequest;
import com.crmpos.sales.controller.ReturnResponse;
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
 * 반품 기록 (SAL-03, BR-11·12). 트랜잭션 밖에서 요청을 검사하고 {@link ReturnWriter}가 한 트랜잭션으로 저장한다.
 * 같은 키는 저장된 반품으로 응답하고(해시가 다르면 409), 같은 키가 서로 다른 판매에 동시에 와서 UNIQUE에 걸려도 같다.
 */
@Service
@RequiredArgsConstructor
public class ReturnRecordingService {

    /** 처음 저장했으면 created=true(201), 같은 키의 다시 보낸 요청이면 false(200). */
    public record Result(ReturnResponse response, boolean created) {
    }

    private final ReturnWriter returnWriter;
    private final StaffRepository staffRepository;
    private final SaleRepository saleRepository;
    private final ObjectMapper objectMapper;

    public Result record(Long saleId, String idempotencyKey, ReturnRequest request, CurrentAccount account) {
        if (account.role() != StaffRole.STORE || account.storeId() == null) {
            throw new BusinessException(ErrorCode.STORE_REQUIRED);
        }
        String key = SalesInputRules.requireKey(idempotencyKey);
        // 권한을 입력 검사보다 먼저: 없는 판매는 404, 다른 매장의 판매는 입력이 틀려도 403 (03 4.2)
        Long saleStoreId = saleRepository.findStoreId(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));
        if (!saleStoreId.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        List<FieldError> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < request.lines().size(); i++) {
            if (!seen.add(request.lines().get(i).lineId())) {
                errors.add(new FieldError("lines[" + i + "].lineId", "같은 줄을 두 번 고를 수 없습니다."));
            }
        }
        SalesInputRules.checkStoreStaff(staffRepository, request.staffId(), account.storeId(), "staffId", errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String requestHash = requestHash(request, saleId, account.storeId());
        try {
            return toResult(returnWriter.record(saleId, account.storeId(), account.id(), key, requestHash, request),
                    requestHash);
        } catch (DataIntegrityViolationException e) {
            // 같은 키가 다른 판매에 동시에 와서 UNIQUE가 하나만 통과시킨 경우: 먼저 저장된 반품을 읽어 응답한다
            return returnWriter.findByKey(key).map(stored -> toResult(stored, requestHash)).orElseThrow(() -> e);
        }
    }

    private static Result toResult(ReturnWriter.Stored stored, String requestHash) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return new Result(stored.response(), stored.created());
    }

    /** 요청 본문과 판매·매장으로 SHA-256을 만든다. 같은 키로 다른 내용이 오면 해시가 달라 거절한다 (ADR-006). */
    String requestHash(ReturnRequest request, Long saleId, Long storeId) {
        try {
            String text = objectMapper.writeValueAsString(request) + "|" + saleId + "|" + storeId;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.");
        }
    }
}
