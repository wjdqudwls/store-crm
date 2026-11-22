package com.crmpos.common;

import org.springframework.http.HttpHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.support.TestAuth;
import com.crmpos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 매장 목록·계정 목록을 기본 데이터(실제 MariaDB)로 확인한다 (03-api.md 3.7). 읽기만 해서 데이터를 만들지 않는다. */
@IntegrationTest
@AutoConfigureMockMvc
class StoreStaffListIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    private long account(String loginId) {
        return jdbc.queryForObject("SELECT id FROM staff WHERE login_id = ?", Long.class, loginId);
    }

    private long gangnamId() {
        return jdbc.queryForObject("SELECT id FROM store WHERE code = 'GN01'", Long.class);
    }

    @Test
    @DisplayName("본사는 매장 목록을 받고 강남점의 활성 직원 수는 직급 계정만 센다")
    void should_listStores_when_hq() throws Exception {
        long expected = jdbc.queryForObject("SELECT COUNT(*) FROM staff s JOIN store st ON s.store_id = st.id "
                + "WHERE st.code = 'GN01' AND s.active = 1 AND s.role IN ('MANAGER','SENIOR','JUNIOR','STAFF')",
                Long.class);

        mockMvc.perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("hq"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code=='GN01')].name").value("강남점"))
                .andExpect(jsonPath("$[?(@.code=='GN01')].activeStaffCount").value((int) expected));
    }

    @Test
    @DisplayName("매장 계정은 매장 목록을 볼 수 없다 (403)")
    void should_forbidStoreList_when_storeAccount() throws Exception {
        mockMvc.perform(get("/api/v1/stores").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("store-gn01"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("매장 계정은 자기 매장의 활성 직원만 받는다: 매장 계정·키오스크는 빠진다")
    void should_listActiveStaffOnly_when_storeAccount() throws Exception {
        mockMvc.perform(get("/api/v1/stores/" + gangnamId() + "/staff").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("store-gn01"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.loginId=='gn01-senior')].role").value("SENIOR"))
                .andExpect(jsonPath("$[?(@.role=='STORE')]").isEmpty())
                .andExpect(jsonPath("$[?(@.role=='KIOSK')]").isEmpty())
                .andExpect(jsonPath("$[?(@.active==false)]").isEmpty());
    }

    @Test
    @DisplayName("본사는 매장의 모든 계정을 받는다: 매장 계정도 보인다")
    void should_listAllAccounts_when_hq() throws Exception {
        mockMvc.perform(get("/api/v1/stores/" + gangnamId() + "/staff").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("hq"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.role=='STORE')]").isNotEmpty());
    }

    @Test
    @DisplayName("다른 매장의 계정 목록은 403, 본사가 없는 매장을 물으면 404")
    void should_return403And404() throws Exception {
        long other = jdbc.queryForObject("SELECT id FROM store WHERE code <> 'GN01' LIMIT 1", Long.class);

        mockMvc.perform(get("/api/v1/stores/" + other + "/staff").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("store-gn01")))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/stores/999999/staff").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("hq"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    @DisplayName("매장 상세: 본사는 어느 매장이나 정보와 활성 직원 수를 받고, 직급 계정만 센다 (B4-7, ADM-03)")
    void should_showStoreDetail_toHq() throws Exception {
        long expected = jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE store_id = ? AND active = 1 "
                + "AND role IN ('MANAGER','SENIOR','JUNIOR','STAFF')", Long.class, gangnamId());

        mockMvc.perform(get("/api/v1/stores/" + gangnamId()).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("hq"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(gangnamId()))
                .andExpect(jsonPath("$.code").value("GN01")).andExpect(jsonPath("$.name").value("강남점"))
                .andExpect(jsonPath("$.activeStaffCount").value((int) expected));
    }

    @Test
    @DisplayName("매장 상세: 매장 계정은 자기 매장만(다른 매장 403), 직원은 403, 없는 매장은 본사에게 404")
    void should_limitStoreDetail_byAccount() throws Exception {
        long other = jdbc.queryForObject("SELECT id FROM store WHERE code <> 'GN01' LIMIT 1", Long.class);

        mockMvc.perform(get("/api/v1/stores/" + gangnamId()).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("store-gn01"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("GN01"));
        mockMvc.perform(get("/api/v1/stores/" + other).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("store-gn01"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/stores/999999").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("store-gn01"))))
                .andExpect(status().isForbidden()); // 다른 매장은 있는지 없는지 알려 주지 않는다
        mockMvc.perform(get("/api/v1/stores/" + gangnamId()).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("gn01-junior"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/stores/999999").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(account("hq"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }
}
