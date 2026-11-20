package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.support.TestAuth;
import com.crmpos.customer.domain.Season;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 컨트롤러부터 DB까지: 등록 → 검색 → 상세 → 수정 (CUS-01·02·04). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class CustomerSearchDetailUpdateIntegrationTest {

    private static final String REGISTER = """
            {"name":"통합 김하늘","phone":"010-2943-4821","nationality":"DOMESTIC",
             "agreements":{"privacy":true,"age14":true,"marketingSms":true,"marketingKakao":false},
             "birthYear":1991,"birthMd":"0314",
             "address":{"sido":"서울","gu":"강남구","detail":"테헤란로 123 401호"},"gender":"F",
             "preferredItemTypes":["OUTER"],
             "sizes":{"top":"M","outer":"90","bottom":"26","shoes":"240"},"contactChannel":"KAKAO"}""";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    CustomerRepository customerRepository;

    private long registerAndGetId(String body) throws Exception {
        String json = mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/customers/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("등록한 고객을 이름 일부로 찾으면 뒷자리 4개만 보이고, 상세에서 전체 번호·등급·가입매장을 본다")
    void should_searchThenShowDetail() throws Exception {
        long id = registerAndGetId(REGISTER);

        mockMvc.perform(get("/api/v1/customers").param("name", "통합 김"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].phoneMasked").value("****-4821"))
                .andExpect(jsonPath("$.content[0].grade").value("NORMAL"))
                .andExpect(jsonPath("$.content[0].staffName").value((Object) null))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("통합 김하늘"))
                .andExpect(jsonPath("$.phone").value("010-2943-4821"))
                .andExpect(jsonPath("$.homeStore.name").value("강남점"))
                .andExpect(jsonPath("$.staff").value((Object) null))
                .andExpect(jsonPath("$.grade.current").value("NORMAL"))
                .andExpect(jsonPath("$.grade.season").value(Season.label(LocalDate.now())))
                .andExpect(jsonPath("$.grade.confirmedStatus").value(Season.isProvisional(LocalDate.now())
                        ? "PROVISIONAL" : "CONFIRMED"))
                .andExpect(jsonPath("$.grade.discountRate").value(0))
                .andExpect(jsonPath("$.grade.confirmedBasis").value((Object) null))
                .andExpect(jsonPath("$.stats.lastVisitAt").value((Object) null))
                .andExpect(jsonPath("$.profile.sizes.outer").value("90"))
                .andExpect(jsonPath("$.profile.preferredItemTypes[0]").value("OUTER"))
                .andExpect(jsonPath("$.marketing.sms").value(true))
                .andExpect(jsonPath("$.marketing.kakao").value(false))
                .andExpect(jsonPath("$.addressDetail").doesNotExist());
    }

    @Test
    @DisplayName("상세주소는 include=address일 때만 복호화해서 준다")
    void should_returnAddressOnlyWhenAsked() throws Exception {
        long id = registerAndGetId(REGISTER);

        mockMvc.perform(get("/api/v1/customers/" + id).param("include", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addressDetail").value("테헤란로 123 401호"));
    }

    @Test
    @DisplayName("이름이 비었으면 빈 목록, 없는 이름은 빈 목록, 없는 고객 상세는 404")
    void should_returnEmptyOrNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/customers")).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/v1/customers").param("name", "  ")).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/customers").param("name", "아무도없는이름"))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get("/api/v1/customers/999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("수정하면 프로필·담당 직원·마케팅 수신이 바뀌고 상세 모양으로 돌려준다")
    void should_updateProfile() throws Exception {
        long id = registerAndGetId(REGISTER);
        Long staffId = staffRepository.findAll().stream()
                .filter(s -> s.getLoginId().equals("gn01-senior")).findFirst().orElseThrow().getId();

        update(id, """
                {"name":"통합 김하나","phone":"010-2943-4821","nationality":"DOMESTIC","gender":"F",
                 "address":{"sido":"서울","gu":"서초구","detail":"서초대로 1"},
                 "sizes":{"outer":"95"},"contactChannel":"SMS","staffId":%d,
                 "marketing":{"sms":false,"kakao":true}}""".formatted(staffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("통합 김하나"))
                .andExpect(jsonPath("$.staff.name").value("강남 시니어"))
                .andExpect(jsonPath("$.profile.regionGu").value("서초구"))
                .andExpect(jsonPath("$.profile.sizes.outer").value("95"))
                .andExpect(jsonPath("$.profile.sizes.top").value((Object) null))
                .andExpect(jsonPath("$.marketing.sms").value(false))
                .andExpect(jsonPath("$.marketing.kakao").value(true))
                .andExpect(jsonPath("$.homeStore.name").value("강남점"));

        mockMvc.perform(get("/api/v1/customers").param("name", "통합 김하나"))
                .andExpect(jsonPath("$.content[0].staffName").value("강남 시니어"));
        mockMvc.perform(get("/api/v1/customers/" + id).param("include", "address"))
                .andExpect(jsonPath("$.addressDetail").value("서초대로 1"));
    }

    @Test
    @DisplayName("번호를 바꾸면 뒷자리가 바뀌고, 다른 고객의 번호로 바꾸면 409로 막힌다")
    void should_changePhoneOrReject() throws Exception {
        long first = registerAndGetId(REGISTER);
        long second = registerAndGetId(REGISTER.replace("010-2943-4821", "010-1111-0937").replace("통합 김하늘", "통합 박도윤"));
        String body = """
                {"name":"통합 김하늘","phone":"%s","nationality":"DOMESTIC","marketing":{"sms":true,"kakao":false}}""";

        update(first, body.formatted("010-5555-7777")).andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("010-5555-7777"));
        mockMvc.perform(get("/api/v1/customers").param("name", "통합 김하늘"))
                .andExpect(jsonPath("$.content[0].phoneMasked").value("****-7777"));

        update(first, body.formatted("+82 10 1111 0937")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_PHONE_DUPLICATED"))
                .andExpect(jsonPath("$.details.customerId").value(second))
                .andExpect(jsonPath("$.details.name").value("통합 박도윤"));
        // 막힌 뒤에도 원래 번호가 그대로다
        mockMvc.perform(get("/api/v1/customers/" + first)).andExpect(jsonPath("$.phone").value("010-5555-7777"));
    }

    @Test
    @DisplayName("직원 직급 계정은 볼 수는 있지만 수정은 403")
    void should_allowReadButNotUpdate_forStaffRank() throws Exception {
        long id = registerAndGetId(REGISTER);
        Long staffAccount = staffRepository.findAll().stream().filter(s -> s.getRole() == StaffRole.STAFF)
                .findFirst().orElseThrow().getId();

        mockMvc.perform(get("/api/v1/customers/" + id).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(staffAccount)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/customers/" + id).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(staffAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","phone":"010-2943-4821","marketing":{"sms":false,"kakao":false}}"""))
                .andExpect(status().isForbidden());
        assertThat(customerRepository.findById(id).orElseThrow().getName()).isEqualTo("통합 김하늘");
    }
}
