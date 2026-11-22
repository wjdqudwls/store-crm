package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.crypto.HmacHasher;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.RequestRateLimiter;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 없는 QR 등록과 키오스크 등록 (REG-01·02·03·05, NFR-04a). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PublicRegistrationIntegrationTest {

    private static final String NONE = ""; // 토큰 없음 (MockMvc가 기본으로 붙이는 매장 계정 토큰을 덮어쓴다)
    private static final String DONE = "등록이 완료되었습니다.";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    HmacHasher hasher;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager em;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    RequestRateLimiter publicRegistrationLimiter;

    @BeforeEach
    void resetLimiter() {
        publicRegistrationLimiter.reset();
    }

    private static String body(String storeCode, String staffCode, String name, String phone) {
        return """
                {%s%s"name":"%s","phone":"%s","nationality":"DOMESTIC",
                 "agreements":{"privacy":true,"age14":true,"marketingSms":true,"marketingKakao":false},
                 "gender":"F","sizes":{"top":"M"}}"""
                .formatted(storeCode == null ? "" : "\"storeCode\":\"" + storeCode + "\",",
                        staffCode == null ? "" : "\"staffCode\":\"" + staffCode + "\",", name, phone);
    }

    private ResultActions qr(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/public/registrations").header(HttpHeaders.AUTHORIZATION, NONE)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Customer find(String normalizedPhone) {
        return customerRepository.findByPhoneHash(hasher.hash(normalizedPhone)).orElseThrow();
    }

    private long storeId(String code) {
        return storeRepository.findByCode(code).orElseThrow().getId();
    }

    private Staff kiosk(String loginId, String storeCode) {
        return staffRepository.saveAndFlush(Staff.create(loginId, passwordEncoder.encode("kiosk1234"), "키오스크",
                StaffRole.KIOSK, null, storeRepository.findByCode(storeCode).orElseThrow(), null,
                LocalDateTime.now(KstTime.ZONE).minusMinutes(5)));
    }

    @Test
    @DisplayName("QR 등록은 로그인 없이 200이고 가입매장·직원 코드의 담당 직원·QR 출처로 저장된다")
    void should_registerByQr_withStoreAndStaff() throws Exception {
        qr(body("GN01", "S002", "김가온", "010-0000-1111"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(DONE));

        Customer customer = find("+821000001111");
        assertThat(customer.getSource()).isEqualTo(CustomerSource.QR);
        assertThat(customer.getHomeStoreId()).isEqualTo(storeId("GN01"));
        assertThat(customer.getStaffId()).isEqualTo(staffRepository.findByLoginId("gn01-senior").orElseThrow().getId());
        assertThat(customer.getName()).isEqualTo("김가온");
    }

    @Test
    @DisplayName("기존 번호로 등록해도 같은 200 응답이고 기존 고객 정보를 바꾸지 않으며 새로 만들지 않는다 (REG-05)")
    void should_returnSameResponse_andKeepExisting_when_phoneExists() throws Exception {
        String first = qr(body("GN01", null, "김가온", "010-0000-2222")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long before = customerRepository.count();

        String second = qr(body("HD01", "S101", "남의이름", "+82 10 0000 2222")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(customerRepository.count()).isEqualTo(before);
        Customer customer = find("+821000002222");
        assertThat(customer.getName()).isEqualTo("김가온");
        assertThat(customer.getHomeStoreId()).isEqualTo(storeId("GN01"));
    }

    @Test
    @DisplayName("직원 코드가 없거나 틀리거나 다른 매장 직원이거나 비활성이어도 등록은 받고 담당 직원만 비운다")
    void should_registerWithoutStaff_when_staffCodeUnusable() throws Exception {
        jdbc.update("UPDATE staff SET active = 0 WHERE login_id = ?", "gn01-junior");
        em.clear();
        String[][] cases = {{"", "010-0000-3001"}, {"NOPE", "010-0000-3002"}, {"S101", "010-0000-3003"}, // 홍대점 직원 코드
                {"S003", "010-0000-3004"}}; // 비활성 직원

        for (String[] c : cases) {
            qr(body("GN01", c[0].isEmpty() ? null : c[0], "김가온", c[1])).andExpect(status().isOk());
            assertThat(find("+82" + c[1].replaceAll("[^0-9]", "").substring(1)).getStaffId()).as(c[0]).isNull();
        }
    }

    @Test
    @DisplayName("없는 매장 코드는 404 STORE_NOT_FOUND, 매장 코드가 비면 400")
    void should_return404_whenUnknownStore_and400_whenMissing() throws Exception {
        qr(body("ZZ99", null, "김가온", "010-0000-4001")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        qr(body(null, null, "김가온", "010-0000-4002")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("storeCode"));
        assertThat(customerRepository.findByPhoneHash(hasher.hash("+821000004001"))).isEmpty();
    }

    @Test
    @DisplayName("필수 동의가 없거나 번호 형식이 틀리면 400이고 fieldErrors를 준다")
    void should_return400_whenInvalid() throws Exception {
        qr(body("GN01", null, "김가온", "12").replace("\"privacy\":true", "\"privacy\":false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='phone')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='agreements.privacy')]").exists());
    }

    @Test
    @DisplayName("같은 IP의 6번째 QR 등록 요청은 429 TOO_MANY_REQUESTS")
    void should_return429_whenSixthRequestFromSameIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            qr(body("GN01", null, "김가온", "010-0000-500" + i)).andExpect(status().isOk());
        }

        qr(body("GN01", null, "김가온", "010-0000-5009")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(customerRepository.findByPhoneHash(hasher.hash("+821000005009"))).isEmpty();
    }

    @Test
    @DisplayName("키오스크 등록은 토큰의 매장으로 가입하고 담당 직원은 비운다")
    void should_registerByKiosk_withTokenStore() throws Exception {
        Staff kiosk = kiosk("pub-kiosk-hd", "HD01");

        mockMvc.perform(post("/api/v1/kiosk/registrations").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(kiosk.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                // 본문에 다른 매장·직원 코드를 보내도 따르지 않는다
                .content(body("GN01", "S002", "김가온", "010-0000-6001")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(DONE));

        Customer customer = find("+821000006001");
        assertThat(customer.getSource()).isEqualTo(CustomerSource.KIOSK);
        assertThat(customer.getHomeStoreId()).isEqualTo(storeId("HD01"));
        assertThat(customer.getStaffId()).isNull();
    }

    @Test
    @DisplayName("키오스크 등록도 기존 번호면 같은 200이고, 키오스크가 아닌 계정은 403, 토큰이 없으면 401")
    void should_protectKioskEndpoint() throws Exception {
        Staff kiosk = kiosk("pub-kiosk-gn", "GN01");
        String json = body(null, null, "김가온", "010-0000-6002");
        String bearer = TestAuth.bearer(kiosk.getId());
        mockMvc.perform(post("/api/v1/kiosk/registrations").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/kiosk/registrations").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.replace("김가온", "남의이름")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(DONE));
        assertThat(find("+821000006002").getName()).isEqualTo("김가온");

        mockMvc.perform(post("/api/v1/kiosk/registrations") // 기본 토큰 = 강남점 매장 계정
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/kiosk/registrations").header(HttpHeaders.AUTHORIZATION, NONE)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("QR 첫 정보는 로그인 없이 매장 이름·동의 문구·사이즈를 준다")
    void should_returnJoinInfo_withoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/public/join-info").param("store", "GN01").param("staff", "NOPE")
                        .header(HttpHeaders.AUTHORIZATION, NONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.code").value("GN01"))
                .andExpect(jsonPath("$.store.name").value("강남점"))
                .andExpect(jsonPath("$.consents.privacy").isNotEmpty())
                .andExpect(jsonPath("$.consents.age14").isNotEmpty())
                .andExpect(jsonPath("$.consents.marketing").isNotEmpty())
                .andExpect(jsonPath("$.sizes[0].itemType").exists());
    }

    @Test
    @DisplayName("없는 매장 코드의 QR 첫 정보는 404 STORE_NOT_FOUND")
    void should_return404_whenJoinInfoStoreUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/public/join-info").param("store", "ZZ99").header(HttpHeaders.AUTHORIZATION, NONE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/public/join-info").header(HttpHeaders.AUTHORIZATION, NONE))
                .andExpect(status().isNotFound());
    }
}
