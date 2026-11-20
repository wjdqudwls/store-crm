package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.crypto.AesGcmCipher;
import com.crmpos.common.crypto.HmacHasher;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.support.TestAuth;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.common.type.GradeCode;
import com.crmpos.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 컨트롤러부터 DB까지: 등록 → 같은 번호 409 → 저장된 값 확인 (REG-01·04·05, NFR-04·04b). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CustomerRegisterIntegrationTest {

    private static final String BODY = """
            {"name":"김하늘","phone":"010-2943-4821","nationality":"DOMESTIC",
             "agreements":{"privacy":true,"age14":true,"marketingSms":true,"marketingKakao":false},
             "birthYear":1991,"birthMd":"0314",
             "address":{"sido":"서울","gu":"강남구","detail":"테헤란로 123 401호"},"gender":"F",
             "preferredItemTypes":["OUTER"],
             "sizes":{"top":"M","outer":"90","bottom":"26","shoes":"240"},"contactChannel":"KAKAO"}""";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerSummaryRepository summaryRepository;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AesGcmCipher cipher;
    @Autowired
    HmacHasher hasher;

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("등록하면 암호화된 번호와 주소, 해시, 뒷자리, 동의 시각, 고객 요약이 저장된다")
    void should_saveEncryptedCustomerWithSummary() throws Exception {
        register(BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("김하늘"))
                .andExpect(jsonPath("$.phoneMasked").value("****-4821"));

        Customer customer = customerRepository.findByPhoneHash(hasher.hash("+821029434821")).orElseThrow();
        assertThat(cipher.decrypt(customer.getPhoneEnc())).isEqualTo("+821029434821");
        assertThat(cipher.decrypt(customer.getAddressDetailEnc())).isEqualTo("테헤란로 123 401호");
        assertThat(customer.getPhoneLast4()).isEqualTo("4821");
        assertThat(customer.getHomeStoreId()).isEqualTo(storeRepository.findByCode("GN01").orElseThrow().getId());
        assertThat(customer.getSource()).isEqualTo(CustomerSource.STORE);
        assertThat(customer.getPrivacyAgreedAt()).isNotNull();
        assertThat(customer.getMarketingSmsAgreedAt()).isNotNull();
        assertThat(customer.getMarketingKakaoAgreedAt()).isNull();
        assertThat(customer.getSizeOuter()).isEqualTo("90");
        assertThat(customer.getPreferredItemTypes()).hasSize(1);

        CustomerSummary summary = summaryRepository.findById(customer.getId()).orElseThrow();
        assertThat(summary.getCurrentGrade()).isEqualTo(GradeCode.NORMAL);
        assertThat(summary.getTotalScore()).isZero();
        assertThat(summary.getPolicyVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("DB를 직접 읽어도 전화번호와 상세주소 평문이 없다")
    void should_notStorePlaintext_when_readRawColumns() throws Exception {
        register(BODY).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT phone_enc, address_detail_enc, phone_hash, phone_last4 FROM customer WHERE phone_last4 = '4821'");

        String phoneEnc = new String((byte[]) row.get("phone_enc"), StandardCharsets.ISO_8859_1);
        String addressEnc = new String((byte[]) row.get("address_detail_enc"), StandardCharsets.ISO_8859_1);
        assertThat(phoneEnc).doesNotContain("1029434821").doesNotContain("010");
        assertThat(addressEnc).doesNotContain("401");
        assertThat(row.get("phone_hash").toString()).hasSize(64).doesNotContain("2943");
    }

    @Test
    @DisplayName("같은 번호는 모양이 달라도 409이고 기존 고객 정보를 돌려주며 새로 만들지 않는다")
    void should_return409_when_samePhoneRegisteredAgain() throws Exception {
        register(BODY).andExpect(status().isCreated());

        register(BODY.replace("010-2943-4821", "+82 10 2943 4821").replace("김하늘", "다른이름"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PHONE_DUPLICATED"))
                .andExpect(jsonPath("$.details.name").value("김하늘"))
                .andExpect(jsonPath("$.details.phoneMasked").value("****-4821"));

        assertThat(customerRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("본사·직원 계정은 등록할 수 없다(403), 동의가 없으면 400이고 아무것도 저장되지 않는다")
    void should_rejectNonStoreAndMissingConsent() throws Exception {
        Long hqId = staffRepository.findAll().stream().filter(s -> s.getRole() == StaffRole.HQ)
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/v1/customers").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(hqId))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        register(BODY.replace("\"privacy\":true", "\"privacy\":false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("agreements.privacy"));

        assertThat(customerRepository.count()).isZero();
        assertThat(summaryRepository.count()).isZero();
    }

    @Test
    @DisplayName("담당 직원을 고르면 그 매장 직원만 되고, 다른 매장 직원이면 400")
    void should_checkStaffBelongsToStore() throws Exception {
        Long gangnamStaff = staffRepository.findAll().stream()
                .filter(s -> s.getLoginId().equals("gn01-senior")).findFirst().orElseThrow().getId();
        Long hongdaeStore = staffRepository.findAll().stream()
                .filter(s -> s.getLoginId().equals("store-hd01")).findFirst().orElseThrow().getId();

        register(BODY.replace("\"gender\":\"F\"", "\"gender\":\"F\",\"staffId\":" + gangnamStaff))
                .andExpect(status().isCreated());
        // 홍대점 매장 계정으로 강남점 직원을 고르면 400
        mockMvc.perform(post("/api/v1/customers").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(hongdaeStore))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("010-2943-4821", "010-1111-2222")
                                .replace("\"gender\":\"F\"", "\"gender\":\"F\",\"staffId\":" + gangnamStaff)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));

        Customer saved = customerRepository.findAll().get(0);
        assertThat(saved.getStaffId()).isEqualTo(gangnamStaff);
    }

    @Test
    @DisplayName("성공·중복·검증 실패 어디서도 로그에 이름·전화번호·주소가 남지 않는다")
    void should_notLogPersonalData(CapturedOutput output) throws Exception {
        register(BODY);
        register(BODY);
        register(BODY.replace("\"privacy\":true", "\"privacy\":false"));
        register(BODY.replace("010-2943-4821", "010-XXXX-4821"));

        assertThat(output.getAll()).doesNotContain("2943").doesNotContain("김하늘").doesNotContain("테헤란로")
                .doesNotContain("1029434821").doesNotContain("010-XXXX");
    }

    @Test
    @DisplayName("동의를 안 하고 전화번호도 틀리게 보내면 두 오류가 한 응답에 함께 온다")
    void should_returnAllFieldErrorsTogether() throws Exception {
        register(BODY.replace("\"privacy\":true", "\"privacy\":false").replace("010-2943-4821", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='agreements.privacy')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='phone')]").exists());

        assertThat(customerRepository.count()).isZero();
    }
}
