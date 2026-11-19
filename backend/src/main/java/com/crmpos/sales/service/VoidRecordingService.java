package com.crmpos.sales.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.controller.SaleDetailResponse;
import com.crmpos.sales.controller.VoidRequest;
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

/**
 * 기록 취소 (SAL-07, BR-16). 매장 계정(자기 매장·판매한 날·마감 전)과 본사(언제나)만 한다. 트랜잭션 밖에서 권한·입력을
 * 확인하고 {@link VoidWriter}가 한 트랜잭션으로 저장한다. 같은 키는 저장된 기록 취소로 응답한다 (ADR-006).
 */
@Service
@RequiredArgsConstructor
public class VoidRecordingService {

    /** 처음 저장했으면 created=true, 같은 키의 다시 보낸 요청이면 false. 응답은 둘 다 200 + 영수증 상세다 (03 4.9). */
    public record Result(SaleDetailResponse sale, boolean created) {
    }

    private final VoidWriter voidWriter;
    private final StaffRepository staffRepository;
    private final SaleRepository saleRepository;

    public Result voidSale(Long saleId, String idempotencyKey, VoidRequest request, CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        String key = SalesInputRules.requireKey(idempotencyKey);
        // 권한을 입력 검사보다 먼저: 없는 판매는 404, 매장 계정이 다른 매장의 판매는 403
        Long saleStoreId = saleRepository.findStoreId(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));
        if (account.role() == StaffRole.STORE && !saleStoreId.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Long staffId;
        if (account.role() == StaffRole.STORE) {
            Long requested = request == null ? null : request.staffId();
            List<FieldError> errors = new ArrayList<>();
            if (requested == null) {
                errors.add(new FieldError("staffId", "처리한 직원을 골라 주세요."));
            } else {
                SalesInputRules.checkStoreStaff(staffRepository, requested, account.storeId(), "staffId", errors);
            }
            if (!errors.isEmpty()) {
                throw new ValidationFailedException(errors);
            }
            staffId = requested;
        } else {
            staffId = account.id();
        }

        String requestHash = requestHash(saleId, staffId, account);
        try {
            return toResult(voidWriter.voidSale(saleId, account.role(), account.storeId(), staffId, account.id(), key,
                    requestHash), requestHash);
        } catch (DataIntegrityViolationException e) {
            return voidWriter.findByKey(key).map(stored -> toResult(stored, requestHash)).orElseThrow(() -> e);
        }
    }

    private static Result toResult(VoidWriter.Stored stored, String requestHash) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return new Result(stored.response(), stored.created());
    }

    /** 기록 취소의 요청 해시: 종류·판매·처리자·역할. 같은 키를 다른 판매·다른 일에 쓰면 해시가 달라 409다. */
    static String requestHash(Long saleId, Long staffId, CurrentAccount account) {
        try {
            String text = "void|" + saleId + "|" + staffId + "|" + account.role() + "|" + account.storeId();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.");
        }
    }
}
