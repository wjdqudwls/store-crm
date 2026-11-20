package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.web.KstTime;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 로그인·토큰·즉시 차단·비밀번호·역할별 접근을 실제 MariaDB와 실제 보안 규칙으로 확인한다 (B0-3, AUTH-01~04, NFR-04c). */
@IntegrationTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    final JsonMapper mapper = JsonMapper.builder().build();
    String loginId;
    Staff staff;

    /** 시험용 직원 계정을 새로 만든다 (기본 계정은 건드리지 않는다). */
    @BeforeEach
    void createAccount() {
        loginId = "auth-" + UUID.randomUUID().toString().substring(0, 8);
        staff = staffRepository.saveAndFlush(Staff.create(loginId, passwordEncoder.encode(PASSWORD), "시험 직원",
                StaffRole.SENIOR, null, storeRepository.findByCode("GN01").orElseThrow(), null,
                LocalDateTime.now(KstTime.ZONE).minusMinutes(5)));
    }

    @AfterEach
    void deleteAccount() {
        jdbc.update("DELETE FROM staff WHERE login_id LIKE 'auth-%'");
    }

    private ResultActions login(String id, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(id, password)));
    }

    private JsonNode loginOk(String password) throws Exception {
        String json = login(loginId, password).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asString();
    }

    private ResultActions me(String authorization) throws Exception {
        var request = get("/api/v1/me");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    // --- 로그인 ---

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 Access·Refresh를 주고, Access로 내 정보를 본다")
    void should_loginAndReadMe_when_credentialsMatch() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);

        assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(tokens.get("expiresIn").asInt()).isEqualTo(1800);
        assertThat(tokens.get("refreshToken").asString()).isNotBlank();
        me(bearer(tokens)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(staff.getId()))
                .andExpect(jsonPath("$.name").value("시험 직원")).andExpect(jsonPath("$.role").value("SENIOR"))
                .andExpect(jsonPath("$.storeCode").value("GN01")).andExpect(jsonPath("$.storeName").value("강남점"))
                .andExpect(jsonPath("$.loginId").value(loginId)).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호가 틀리거나 없는 아이디면 같은 401 AUTH_INVALID_CREDENTIALS다")
    void should_return401_when_credentialsWrong() throws Exception {
        login(loginId, "Wrong1234").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS")).andExpect(jsonPath("$.traceId").exists());
        login("no-such-account", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("빈 아이디·비밀번호는 400 VALIDATION_FAILED, 비활성 계정은 401 AUTH_ACCOUNT_DISABLED")
    void should_rejectBlankAndDisabled() throws Exception {
        login("", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        jdbc.update("UPDATE staff SET active = 0 WHERE id = ?", staff.getId());
        login(loginId, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    // --- 로그인 잠금: 5번 연속 틀리면 10분 ---

    private void fail(String id, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(id, "Wrong1234").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }
    }

    @Test
    @DisplayName("4번 틀린 뒤 맞으면 로그인되고 횟수가 0으로 돌아간다 (다시 4번 틀려도 안 잠긴다)")
    void should_resetCount_when_successAfterFourFailures() throws Exception {
        fail(loginId, 4);
        login(loginId, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM staff WHERE id = ?", Integer.class,
                staff.getId())).isZero();

        fail(loginId, 4);
        login(loginId, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("5번째로 틀리면 잠기고, 잠긴 동안은 맞는 비밀번호도 429 AUTH_LOGIN_LOCKED다")
    void should_lock_when_fifthFailure() throws Exception {
        fail(loginId, 5);

        login(loginId, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_LOCKED")).andExpect(jsonPath("$.traceId").exists());
        login(loginId, "Wrong1234").andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT locked_until FROM staff WHERE id = ?", java.sql.Timestamp.class,
                staff.getId())).isNotNull();
    }

    @Test
    @DisplayName("잠금은 10분 뒤에 풀려 맞는 비밀번호로 로그인되고, 풀린 뒤에는 처음부터 다시 센다")
    void should_unlock_after10Minutes() throws Exception {
        fail(loginId, 5);
        jdbc.update("UPDATE staff SET locked_until = ? WHERE id = ?", LocalDateTime.now(KstTime.ZONE).minusSeconds(1),
                staff.getId());

        fail(loginId, 4); // 풀린 뒤 4번 틀려도 아직 안 잠긴다 (횟수는 잠글 때 0으로 돌아갔다)
        login(loginId, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT locked_until FROM staff WHERE id = ?", java.sql.Timestamp.class,
                staff.getId())).isNull();
    }

    @Test
    @DisplayName("없는 아이디도 같은 규칙으로 잠겨서 응답으로 아이디가 있는지 알 수 없다")
    void should_lockUnknownLoginId_same_as_real() throws Exception {
        String ghost = "ghost-" + UUID.randomUUID().toString().substring(0, 8);

        fail(ghost, 5);

        login(ghost, PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_LOCKED"));
    }

    @Test
    @DisplayName("다른 계정의 실패는 세지 않는다 (잠긴 계정이 있어도 내 계정은 로그인된다)")
    void should_countPerAccount() throws Exception {
        fail("hq", 3);
        fail(loginId, 5);

        login("hq", "hqPass1234").andExpect(status().isOk());
        login(loginId, PASSWORD).andExpect(status().isTooManyRequests());
    }

    // --- 요청마다 확인 ---

    @Test
    @DisplayName("토큰이 없거나 깨졌으면 401 AUTH_TOKEN_INVALID, 같은 에러 형식(traceId 포함)")
    void should_return401_when_noOrBrokenToken() throws Exception {
        me("").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.traceId").exists());
        me("Bearer abc.def.ghi").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, "Basic xxx"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 토큰은 401 AUTH_TOKEN_EXPIRED")
    void should_return401_when_expired() throws Exception {
        Instant longAgo = Instant.now().minusSeconds(3600);
        String expired = new JwtService(TestAuth.SECRET, Clock.fixed(longAgo, ZoneOffset.UTC)).issueAccess(staff.getId());

        me("Bearer " + expired).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("비활성화하면 이미 받은 Access 토큰도 바로 401 AUTH_ACCOUNT_DISABLED")
    void should_blockAtOnce_when_deactivated() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);
        me(bearer(tokens)).andExpect(status().isOk());

        jdbc.update("UPDATE staff SET active = 0 WHERE id = ?", staff.getId());

        me(bearer(tokens)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("로그인 기준 시각보다 먼저 발급된 토큰은 401 AUTH_TOKEN_INVALID")
    void should_reject_when_issuedBeforeValidAfter() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);

        // 서버 시각은 한국 시간 기준이다 (CI는 UTC라 LocalDateTime.now()를 쓰면 9시간 어긋난다)
        jdbc.update("UPDATE staff SET tokens_valid_after = ? WHERE id = ?", LocalDateTime.now(KstTime.ZONE).plusMinutes(1),
                staff.getId());

        me(bearer(tokens)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    // --- 재발급 ---

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    @Test
    @DisplayName("Refresh로 Access와 Refresh를 새로 받는다 (공개 주소, 토큰 없이)")
    void should_issueNewTokens_when_refresh() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);

        String json = refresh(tokens.get("refreshToken").asString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists()).andReturn().getResponse().getContentAsString();

        me("Bearer " + mapper.readTree(json).get("accessToken").asString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Access 토큰으로 재발급하거나 비활성 계정이면 401")
    void should_rejectRefresh_when_wrongTypeOrDisabled() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);

        refresh(tokens.get("accessToken").asString()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));

        jdbc.update("UPDATE staff SET active = 0 WHERE id = ?", staff.getId());
        refresh(tokens.get("refreshToken").asString()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    // --- 비밀번호·모든 기기 로그아웃 ---

    private ResultActions changePassword(String authorization, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 이전 로그인(Access·Refresh)이 모두 끊기고, 새 토큰과 새 비밀번호로 계속 쓴다")
    void should_cutOldLogins_when_passwordChanged() throws Exception {
        JsonNode other = loginOk(PASSWORD); // 다른 기기
        JsonNode mine = loginOk(PASSWORD);

        String json = changePassword(bearer(mine), PASSWORD, "NewPass123").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode renewed = mapper.readTree(json);

        me(bearer(other)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        refresh(other.get("refreshToken").asString()).andExpect(status().isUnauthorized());
        me(bearer(renewed)).andExpect(status().isOk());
        login(loginId, PASSWORD).andExpect(status().isUnauthorized());
        login(loginId, "NewPass123").andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 PASSWORD_MISMATCH, 새 비밀번호가 규칙에 안 맞으면 400 PASSWORD_POLICY_VIOLATION")
    void should_rejectPasswordChange_when_mismatchOrPolicy() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);

        changePassword(bearer(tokens), "Wrong1234", "NewPass123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        changePassword(bearer(tokens), PASSWORD, "short1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
        changePassword(bearer(tokens), PASSWORD, "onlyletters").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
        me(bearer(tokens)).andExpect(status().isOk()); // 실패하면 로그인은 그대로
    }

    @Test
    @DisplayName("모든 기기에서 로그아웃하면 이전 토큰이 모두 끊기고 다시 로그인하면 된다")
    void should_cutAllLogins_when_logoutAll() throws Exception {
        JsonNode tokens = loginOk(PASSWORD);

        mockMvc.perform(post("/api/v1/auth/logout-all").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isNoContent());

        me(bearer(tokens)).andExpect(status().isUnauthorized());
        refresh(tokens.get("refreshToken").asString()).andExpect(status().isUnauthorized());
        me(bearer(loginOk(PASSWORD))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("매장 계정은 비밀번호 바꾸기·모든 기기 로그아웃을 못 한다 (본사가 다시 정한다): 403")
    void should_forbidStoreAccount_onPersonalPasswordApis() throws Exception {
        long store = jdbc.queryForObject("SELECT id FROM staff WHERE login_id = 'store-gn01'", Long.class);
        String token = TestAuth.bearer(store);

        changePassword(token, "x", "NewPass123").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/auth/logout-all").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    // --- 역할별 접근 ---

    @Test
    @DisplayName("키오스크는 내 정보만 되고 다른 API는 403 ACCESS_DENIED")
    void should_limitKiosk_toMeAndKioskApis() throws Exception {
        Staff kiosk = staffRepository.saveAndFlush(Staff.create("auth-kiosk", passwordEncoder.encode(PASSWORD),
                "강남 키오스크", StaffRole.KIOSK, null, storeRepository.findByCode("GN01").orElseThrow(), null,
                LocalDateTime.now(KstTime.ZONE).minusMinutes(5)));
        String token = TestAuth.bearer(kiosk.getId());

        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("KIOSK"));
        mockMvc.perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").exists());
        mockMvc.perform(get("/api/v1/customers").param("name", "김").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공개 주소(사이즈 목록)는 토큰 없이 열리고, 화면 파일·health는 로그인 없이 열린다")
    void should_openPublicPaths_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/public/sizes")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/customers/12")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("기본 계정(본사·매장 계정)은 환경변수 비밀번호로 로그인된다")
    void should_loginSeededAccounts() throws Exception {
        login("hq", "hqPass1234").andExpect(status().isOk());
        login("store-gn01", "storePass1234").andExpect(status().isOk());
        login("store-hd01", "storePass1234").andExpect(status().isOk());
        login("gn01-senior", "staffPass1234").andExpect(status().isOk());
        login("hq", "wrongPass1").andExpect(status().isUnauthorized());
    }
}
