package com.crmpos.common.security;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로그인(B0-3) 전에 쓰는 임시 구현. 헤더 {@code X-Dev-Account-Id}의 계정 ID로 계정을 고르고,
 * 헤더가 없으면 첫 활성 매장 계정으로 본다. prod 프로필에서는 꺼진다. B0-3에서 지운다 (ADR-032).
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DevCurrentAccountProvider implements CurrentAccountProvider {

    public static final String HEADER = "X-Dev-Account-Id";

    private final StaffRepository staffRepository;

    @Override
    public CurrentAccount current(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        Staff staff = (header == null || header.isBlank()) ? defaultStoreAccount() : accountOf(header.trim());
        if (!staff.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        Long storeId = staff.getStore() == null ? null : staff.getStore().getId();
        return new CurrentAccount(staff.getId(), staff.getRole(), storeId);
    }

    private Staff defaultStoreAccount() {
        return staffRepository.findFirstByRoleAndActiveTrueOrderByIdAsc(StaffRole.STORE)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
    }

    private Staff accountOf(String header) {
        long id;
        try {
            id = Long.parseLong(header);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return staffRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
    }
}
