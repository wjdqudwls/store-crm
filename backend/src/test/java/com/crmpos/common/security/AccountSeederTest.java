package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 기본 계정 시드: 계정 8개, 비밀번호는 환경변수 값의 BCrypt, 다시 실행해도 늘지 않고 바꾼 비밀번호를 덮어쓰지 않는다 (ADR-019). */
@IntegrationTest
class AccountSeederTest {

    @Autowired
    AccountSeeder seeder;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("본사 1·매장 계정 2·직원 5가 있고 비밀번호는 평문이 아닌 BCrypt로 저장된다")
    void should_haveBaseAccounts_withBcryptPasswords() {
        assertThat(staffRepository.findByLoginId("hq")).get()
                .satisfies(s -> {
                    assertThat(s.getPasswordHash()).startsWith("$2").doesNotContain("hqPass1234");
                    assertThat(passwordEncoder.matches("hqPass1234", s.getPasswordHash())).isTrue();
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE login_id IN ('hq','store-gn01','store-hd01',"
                + "'gn01-manager','gn01-senior','gn01-junior','gn01-staff','hd01-senior')", Integer.class)).isEqualTo(8);
    }

    @Test
    @DisplayName("다시 실행해도 계정이 늘지 않고, 이미 바꾼 비밀번호는 덮어쓰지 않는다")
    void should_beIdempotent_andKeepChangedPassword() throws Exception {
        String before = jdbc.queryForObject("SELECT password_hash FROM staff WHERE login_id = 'hq'", String.class);
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM staff", Integer.class);

        seeder.run(null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff", Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM staff WHERE login_id = 'hq'", String.class))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("로그인 전(B0-6)에 임시 값으로 만든 계정은 비밀번호만 채운다")
    void should_fillPassword_whenPlaceholder() throws Exception {
        String original = jdbc.queryForObject("SELECT password_hash FROM staff WHERE login_id = 'hd01-senior'", String.class);
        jdbc.update("UPDATE staff SET password_hash = ? WHERE login_id = 'hd01-senior'", AccountSeeder.NO_LOGIN);

        seeder.run(null);

        String hash = jdbc.queryForObject("SELECT password_hash FROM staff WHERE login_id = 'hd01-senior'", String.class);
        assertThat(passwordEncoder.matches("staffPass1234", hash)).isTrue();
        assertThat(hash).isNotEqualTo(AccountSeeder.NO_LOGIN);
        jdbc.update("UPDATE staff SET password_hash = ? WHERE login_id = 'hd01-senior'", original);
    }

    @Test
    @DisplayName("비밀번호 값이 비었거나 규칙에 안 맞으면 시작하지 않는다 (prod에서 비어 있으면 실패)")
    void should_failToStart_whenPasswordBlankOrWeak() {
        AccountSeeder weak = new AccountSeeder(staffRepository, null, passwordEncoder);
        org.springframework.test.util.ReflectionTestUtils.setField(weak, "hqPassword", "");
        org.springframework.test.util.ReflectionTestUtils.setField(weak, "storePassword", "storePass1234");
        org.springframework.test.util.ReflectionTestUtils.setField(weak, "staffPassword", "staffPass1234");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> weak.run(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
