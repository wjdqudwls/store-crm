package com.crmpos.common.security;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인(B0-3) 전에 쓰는 데모 계정. 계정이 하나도 없을 때만 넣는다. prod에서는 돌지 않는다.
 * 비밀번호 칸은 로그인할 수 없는 표시값이다. B0-3에서 환경변수 비밀번호와 BCrypt 구현으로 바꾼다 (ADR-019).
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DevAccountSeeder implements ApplicationRunner {

    static final String NO_LOGIN = "!dev-no-login";

    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (staffRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Store gangnam = storeRepository.findByCode("GN01").orElseThrow();
        Store hongdae = storeRepository.findByCode("HD01").orElseThrow();

        staffRepository.save(account("hq", "본사 담당", StaffRole.HQ, null, null, null, now));
        staffRepository.save(account("store-gn01", "강남점 매장", StaffRole.STORE, null, gangnam, null, now));
        staffRepository.save(account("store-hd01", "홍대점 매장", StaffRole.STORE, null, hongdae, null, now));
        staffRepository.save(account("gn01-manager", "강남 매니저", StaffRole.MANAGER, ManagerSlot.MAIN, gangnam, "S001", now));
        staffRepository.save(account("gn01-senior", "강남 시니어", StaffRole.SENIOR, null, gangnam, "S002", now));
        staffRepository.save(account("gn01-junior", "강남 주니어", StaffRole.JUNIOR, null, gangnam, "S003", now));
        staffRepository.save(account("gn01-staff", "강남 스태프", StaffRole.STAFF, null, gangnam, "S004", now));
        staffRepository.save(account("hd01-senior", "홍대 시니어", StaffRole.SENIOR, null, hongdae, "S101", now));
        log.info("dev accounts created count={}", staffRepository.count());
    }

    private static Staff account(String loginId, String name, StaffRole role, ManagerSlot slot, Store store,
            String staffCode, LocalDateTime now) {
        return Staff.create(loginId, NO_LOGIN, name, role, slot, store, staffCode, now);
    }
}
