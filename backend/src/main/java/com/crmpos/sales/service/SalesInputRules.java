package com.crmpos.sales.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 판매·반품이 같이 쓰는 입력 검사: 요청 키(Idempotency-Key)와 매장의 활성 직원. 오류에는 필드 이름과 이유만 담는다. */
final class SalesInputRules {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<StaffRole> STAFF_RANKS =
            Set.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private SalesInputRules() {
    }

    /** 요청 키가 UUID가 아니면 400. 대소문자는 구분하지 않아 소문자로 통일한다 (ADR-006). */
    static String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || !UUID_FORMAT.matcher(idempotencyKey).matches()) {
            throw new ValidationFailedException(
                    List.of(new FieldError("Idempotency-Key", "UUID 형식의 요청 키가 필요합니다.")));
        }
        return idempotencyKey.toLowerCase();
    }

    /**
     * 그 매장의 활성 직원(MANAGER~STAFF)인지 본다. 없는 ID는 404, 다른 매장·비활성·매장 계정이면 field 오류를 더한다.
     */
    static Staff checkStoreStaff(StaffRepository staffRepository, Long staffId, Long storeId, String field,
            List<FieldError> errors) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        boolean sameStore = staff.getStore() != null && staff.getStore().getId().equals(storeId);
        if (!staff.isActive() || !sameStore || !STAFF_RANKS.contains(staff.getRole())) {
            errors.add(new FieldError(field, "이 매장의 활성 직원이 아닙니다."));
        }
        return staff;
    }
}
