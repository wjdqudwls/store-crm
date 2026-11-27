package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 가족 구성원 추가·수정·삭제·목록과 착용자 호칭 보존 (CUS-05, BR-13). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MemberIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager em;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private long customer(String phone) throws Exception {
        String body = """
                {"name":"김가온","phone":"%s","agreements":{"privacy":true,"age14":true}}""".formatted(phone);
        return mapper.readTree(send(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        return mockMvc.perform(builder);
    }

    private ResultActions add(long customerId, String json) throws Exception {
        return send(post("/api/v1/customers/" + customerId + "/members").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long addOk(long customerId, String json) throws Exception {
        return mapper.readTree(add(customerId, json).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asLong();
    }

    private String bearer(String loginId) {
        return TestAuth.bearer(staffRepository.findByLoginId(loginId).orElseThrow().getId());
    }

    @Test
    @DisplayName("추가(201) → 목록 → 수정(전체 교체) → 삭제(204)")
    void should_manageMembers() throws Exception {
        long customerId = customer("010-0000-9001");

        long id = addOk(customerId, """
                {"nickname":" 남편 ","relation":"SPOUSE","gender":"M","birthYear":1989,
                 "sizes":{"top":"L","outer":"105","bottom":"32","shoes":"270"}}""");
        send(get("/api/v1/customers/" + customerId + "/members")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value("남편"))
                .andExpect(jsonPath("$[0].sizes.outer").value("105"));

        send(patch("/api/v1/members/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"아빠\",\"relation\":\"PARENT\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("아빠"))
                .andExpect(jsonPath("$.gender").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sizes.top").value(org.hamcrest.Matchers.nullValue()));

        send(delete("/api/v1/members/" + id)).andExpect(status().isNoContent());
        send(get("/api/v1/customers/" + customerId + "/members")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("자녀는 아동 사이즈와 직접 입력 값(20자까지)을 받고, 성인 관계는 허용값만 받는다")
    void should_allowFreeSizeForChildOnly() throws Exception {
        long customerId = customer("010-0000-9002");

        add(customerId, """
                {"nickname":"첫째 딸","relation":"CHILD","gender":"F","sizes":{"top":"8","outer":"110","bottom":"키120"}}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sizes.outer").value("110"));
        add(customerId, """
                {"nickname":"둘째","relation":"CHILD","sizes":{"top":"아주아주아주아주아주아주아주긴사이즈값입니다"}}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("sizes.top"));
        add(customerId, """
                {"nickname":"남편","relation":"SPOUSE","gender":"M","sizes":{"outer":"8","bottom":"999"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='sizes.outer')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='sizes.bottom')]").exists());
    }

    @Test
    @DisplayName("호칭·관계가 없거나 출생연도가 이상하면 400")
    void should_return400_whenInvalid() throws Exception {
        long customerId = customer("010-0000-9003");

        add(customerId, "{\"nickname\":\"  \",\"birthYear\":1800}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='nickname')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='relation')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='birthYear')]").exists());
    }

    @Test
    @DisplayName("없는 고객·구성원은 404, 직원은 조회만 되고 쓰기는 403, 본사도 쓰기 403, 토큰 없으면 401")
    void should_checkRolesAndNotFound() throws Exception {
        long customerId = customer("010-0000-9004");
        long id = addOk(customerId, "{\"nickname\":\"어머니\",\"relation\":\"PARENT\"}");
        String json = "{\"nickname\":\"x\",\"relation\":\"OTHER\"}";

        add(999999, json).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        send(get("/api/v1/customers/999999/members")).andExpect(status().isNotFound());
        send(patch("/api/v1/members/999999").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        send(delete("/api/v1/members/999999")).andExpect(status().isNotFound());

        for (String who : new String[] {"gn01-junior", "hq"}) {
            send(get("/api/v1/customers/" + customerId + "/members").header(HttpHeaders.AUTHORIZATION, bearer(who)))
                    .andExpect(status().isOk());
            send(post("/api/v1/customers/" + customerId + "/members").header(HttpHeaders.AUTHORIZATION, bearer(who))
                    .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
            send(delete("/api/v1/members/" + id).header(HttpHeaders.AUTHORIZATION, bearer(who))).andExpect(status().isForbidden());
        }
        send(get("/api/v1/customers/" + customerId + "/members").header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("판매에서 착용자를 고르면 호칭이 복사되고 구성원을 지워도 영수증은 그대로, 다른 고객의 구성원은 404")
    void should_keepWearerLabel_andRejectOtherCustomersMember() throws Exception {
        long first = customer("010-0000-9005");
        long second = customer("010-0000-9006");
        long child = addOk(first, "{\"nickname\":\"첫째 딸\",\"relation\":\"CHILD\"}");
        long seller = staffRepository.findByLoginId("gn01-senior").orElseThrow().getId();
        String sale = "{\"sellerStaffId\":%d,\"customerId\":%d,\"lines\":[{\"productName\":\"모자\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":1,\"lineAmount\":1000,\"memberId\":%d}]}";

        send(post("/api/v1/sales").header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(sale.formatted(seller, second, child)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        long saleId = mapper.readTree(send(post("/api/v1/sales").header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(sale.formatted(seller, first, child)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        send(delete("/api/v1/members/" + child)).andExpect(status().isNoContent());
        em.flush();

        assertThat(jdbc.queryForObject("SELECT wearer_label FROM sale_line WHERE sale_id = ?", String.class, saleId))
                .isEqualTo("첫째 딸");
        send(get("/api/v1/sales/" + saleId)).andExpect(jsonPath("$.lines[0].wearerLabel").value("첫째 딸"));
    }
}
