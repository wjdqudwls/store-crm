package com.crmpos.customer.service;

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
import com.crmpos.customer.controller.PublicRegisterRequest;
import com.crmpos.customer.domain.CustomerSource;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * QR·키오스크 등록 (REG-02·03·05). 로그인 없는 공개 등록이라 가입 여부를 드러내지 않는다: 새 번호든 기존 번호든 같은 결과로 끝난다.
 * QR은 매장 코드로 가입매장을, 직원 코드로 담당 직원을 정한다(직원 코드가 없거나 틀리거나 비활성이거나 다른 매장이면 등록은 받고
 * 담당 직원만 비운다). 키오스크는 토큰의 매장으로 가입하고 담당 직원은 비운다.
 */
@Service
@RequiredArgsConstructor
public class PublicRegistrationService {

    static final Set<StaffRole> STAFF_RANKS = Set.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private final CustomerRegistrationService registrationService;
    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;

    /** QR 등록. 없는(또는 닫힌) 매장 코드는 404 STORE_NOT_FOUND. */
    public void registerByQr(PublicRegisterRequest request) {
        if (request.storeCode() == null || request.storeCode().isBlank()) {
            throw new ValidationFailedException(List.of(new FieldError("storeCode", "매장 코드가 필요합니다.")));
        }
        Store store = findActiveStore(request.storeCode());
        Staff staff = findStaffByCode(store.getId(), request.staffCode());
        registrationService.registerBySelf(request.toCustomerRequest(), store.getId(), staff, CustomerSource.QR);
    }

    /** 키오스크 등록. 매장은 토큰에서 정하고 담당 직원은 비운다. */
    public void registerByKiosk(PublicRegisterRequest request, CurrentAccount account) {
        registrationService.registerBySelf(request.toCustomerRequest(), account.storeId(), null, CustomerSource.KIOSK);
    }

    Store findActiveStore(String storeCode) {
        return storeRepository.findByCode(storeCode.trim()).filter(Store::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    /** 직원 코드가 이 매장의 활성 직원 것이 아니면 null (오류로 알려 주지 않는다). */
    Staff findStaffByCode(Long storeId, String staffCode) {
        if (staffCode == null || staffCode.isBlank()) {
            return null;
        }
        return staffRepository.findFirstByStoreIdAndStaffCode(storeId, staffCode.trim())
                .filter(s -> s.isActive() && STAFF_RANKS.contains(s.getRole())).orElse(null);
    }
}
