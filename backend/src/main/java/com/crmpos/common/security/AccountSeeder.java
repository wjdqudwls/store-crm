package com.crmpos.common.security;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.web.KstTime;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기본 계정을 앱이 켜질 때 코드로 넣는다 (ADR-019, B0-3): 본사 1, 매장 계정 2, 데모 직원. 비밀번호는 환경변수로 받아
 * BCrypt로 저장하고 코드·설정에 쓰지 않는다 (NFR-06). 이미 있는 계정은 건드리지 않는다. 다만 로그인 전(B0-6)에
 * 임시 값으로 만든 계정은 비밀번호만 채운다. 직원 등록 API(ADM-01)가 생기기 전까지의 시작용 데이터다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSeeder implements ApplicationRunner {

    /** 로그인 전(B0-6)에 임시 계정을 만들 때 쓰던 비밀번호 자리값. 로그인이 불가능한 값이다. */
    static final String NO_LOGIN = "!dev-no-login";

    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${crm.accounts.hq-password}")
    private String hqPassword;
    @Value("${crm.accounts.store-gn01-password}")
    private String storeGn01Password;
    @Value("${crm.accounts.store-hd01-password}")
    private String storeHd01Password;
    @Value("${crm.accounts.staff-password}")
    private String staffPassword;

    private record Seed(String loginId, String name, StaffRole role, ManagerSlot slot, String storeCode,
            String staffCode, String password) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        List<Seed> seeds = List.of(
                new Seed("hq", "본사 담당", StaffRole.HQ, null, null, null, hqPassword),
                new Seed("store-gn01", "강남점 매장", StaffRole.STORE, null, "GN01", null, storeGn01Password),
                new Seed("store-hd01", "홍대점 매장", StaffRole.STORE, null, "HD01", null, storeHd01Password),
                new Seed("gn01-manager", "강남 매니저", StaffRole.MANAGER, ManagerSlot.MAIN, "GN01", "S001", staffPassword),
                new Seed("gn01-senior", "강남 시니어", StaffRole.SENIOR, null, "GN01", "S002", staffPassword),
                new Seed("gn01-junior", "강남 주니어", StaffRole.JUNIOR, null, "GN01", "S003", staffPassword),
                new Seed("gn01-staff", "강남 스태프", StaffRole.STAFF, null, "GN01", "S004", staffPassword),
                new Seed("hd01-senior", "홍대 시니어", StaffRole.SENIOR, null, "HD01", "S101", staffPassword));
        int created = 0;
        for (Seed seed : seeds) {
            Staff existing = staffRepository.findByLoginId(seed.loginId()).orElse(null);
            if (existing == null) {
                Store store = seed.storeCode() == null ? null : storeRepository.findByCode(seed.storeCode()).orElseThrow();
                staffRepository.save(Staff.create(seed.loginId(), passwordEncoder.encode(seed.password()), seed.name(),
                        seed.role(), seed.slot(), store, seed.staffCode(), now));
                created++;
            } else if (NO_LOGIN.equals(existing.getPasswordHash())) {
                existing.changePassword(passwordEncoder.encode(seed.password()), now);
            }
        }
        log.info("accounts seeded created={}", created);
    }
}
