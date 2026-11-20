package com.crmpos.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 콜리스트를 실제 MariaDB로 확인한다: 필터 조합, 구매 없는 고객, "이 매장 고객" 정의, 정렬, 권한 (CAL-01, BR-09). 롤백한다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class CallListIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerSummaryRepository summaryRepository;
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager em;

    int seq = 0;
    final LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));

    private long id(String loginId) {
        return jdbc.queryForObject("SELECT id FROM staff WHERE login_id = ?", Long.class, loginId);
    }

    private long storeId(String code) {
        return jdbc.queryForObject("SELECT id FROM store WHERE code = ?", Long.class, code);
    }

    /** 고객을 만든다. 가입 매장·담당·홍보 동의·등급·마지막 방문일(null이면 구매 없음)·가입일을 정한다. */
    private long customer(String name, Long homeStore, Long staffId, boolean marketing, String grade,
            LocalDateTime lastVisit, LocalDateTime joinedAt) {
        CustomerDraft draft = CustomerDraft.builder().name(name).nationality(Nationality.DOMESTIC).phoneEnc(new byte[] {1})
                .phoneHash(String.format("%064d", ++seq)).phoneLast4("4821").privacyAgreed(true).age14Confirmed(true)
                .homeStoreId(homeStore).staffId(staffId).source(CustomerSource.STORE).marketingSms(marketing).build();
        Customer c = customerRepository.saveAndFlush(Customer.register(draft, joinedAt));
        summaryRepository.saveAndFlush(CustomerSummary.initial(c.getId(), 1, joinedAt));
        jdbc.update("UPDATE customer_summary SET current_grade = ?, last_visit_at = ? WHERE customer_id = ?", grade, lastVisit,
                c.getId());
        em.clear();
        return c.getId();
    }

    private void purchase(long customerId, long storeId, String target, String itemType, int qty, int voided) {
        jdbc.update("INSERT INTO customer_purchase (customer_id, sale_id, sale_line_id, store_id, sold_at, purchase_group_id, "
                + "counted_at, target, item_type, qty, amount, voided_qty, returned_qty) VALUES (?, ?, ?, ?, NOW(6), ?, NOW(6), ?, ?, ?, 1000, ?, ?)",
                customerId, 700000 + seq, 800000 + seq * 10 + customerId % 7, storeId, 700000 + seq, target, itemType, qty, voided, voided);
        seq++;
    }

    private ResultActions call(String account, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/call-list" + query).header(HttpHeaders.AUTHORIZATION,
                TestAuth.bearer(id(account))));
    }

    @Test
    @DisplayName("직원 기본값(내 담당·D 이상·90일 이상 미방문): 조건에 맞는 고객만 마지막 방문이 오래된 순으로 나온다")
    void should_applyEmployeeDefaults_oldestFirst() throws Exception {
        long junior = id("gn01-junior");
        long gn = storeId("GN01");
        long oldVisit = customer("오래전", gn, junior, true, "B", now.minusDays(200), now.minusDays(400));
        long midVisit = customer("조금전", gn, junior, true, "D", now.minusDays(100), now.minusDays(400));
        customer("최근방문", gn, junior, true, "B", now.minusDays(10), now.minusDays(400)); // 90일 안 → 제외
        customer("일반등급", gn, junior, true, "NORMAL", now.minusDays(200), now.minusDays(400)); // D 미만 → 제외
        customer("남의담당", gn, id("gn01-senior"), true, "B", now.minusDays(200), now.minusDays(400)); // 제외

        call("gn01-junior", "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(oldVisit)).andExpect(jsonPath("$.content[1].id").value(midVisit))
                .andExpect(jsonPath("$.content[0].phoneMasked").value("****-4821")).andExpect(jsonPath("$.content[0].grade").value("B"))
                .andExpect(jsonPath("$.content[0].staffName").value("강남 주니어")).andExpect(jsonPath("$.content[0].marketingAllowed").value(true))
                .andExpect(jsonPath("$.content[0].lastVisitAt").exists()).andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    @Test
    @DisplayName("구매 없는 고객은 가입일로 미방문 일수를 세고 lastVisitAt은 null이다 (BR-09)")
    void should_useJoinDate_whenNoPurchase() throws Exception {
        long junior = id("gn01-junior");
        long gn = storeId("GN01");
        long old = customer("가입만오래", gn, junior, true, "D", null, now.minusDays(120));
        customer("가입한지얼마", gn, junior, true, "D", null, now.minusDays(5));

        call("gn01-junior", "").andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(old))
                .andExpect(jsonPath("$.content[0].lastVisitAt").doesNotExist());
    }

    @Test
    @DisplayName("매장 계정 기본값: 이 매장 고객 전체(가입·구매·매장 계정이 맡은 고객)이고 홍보 연락 가능한 고객만, 다른 매장 고객은 빠진다")
    void should_defineStoreCustomers() throws Exception {
        long gn = storeId("GN01");
        long hd = storeId("HD01");
        long joined = customer("강남가입", gn, null, true, "NORMAL", null, now.minusDays(3));
        long bought = customer("강남구매", hd, null, true, "NORMAL", now.minusDays(2), now.minusDays(300));
        purchase(bought, gn, "WOMEN", "OUTER", 1, 0);
        long storeOwned = customer("매장이맡음", null, id("store-gn01"), true, "NORMAL", null, now.minusDays(9));
        customer("홍보불가", gn, null, false, "NORMAL", null, now.minusDays(4)); // promoOnly 기본 true → 제외
        customer("홍대만", hd, null, true, "NORMAL", null, now.minusDays(4)); // 다른 매장 → 제외
        long voidedOnly = customer("취소구매뿐", hd, null, true, "NORMAL", null, now.minusDays(4));
        purchase(voidedOnly, gn, "WOMEN", "OUTER", 1, 1); // 기록 취소된 구매는 이 매장에서 산 것이 아니다 → 제외

        call("store-gn01", "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.id==" + joined + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id==" + bought + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id==" + storeOwned + ")]").isNotEmpty());
        call("store-gn01", "?promoOnly=false").andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @DisplayName("최소 등급은 컷 순서로 비교하고(B 이상 = B·A), 담당 직원·미방문 일수·대상·품목·가족 관계 조건이 함께 걸린다")
    void should_combineFilters() throws Exception {
        long gn = storeId("GN01");
        long match = customer("모두맞음", gn, id("gn01-senior"), true, "A", now.minusDays(150), now.minusDays(400));
        purchase(match, gn, "WOMEN", "OUTER", 1, 0);
        jdbc.update("INSERT INTO customer_member (customer_id, nickname, relation, created_at) VALUES (?, '첫째', 'CHILD', NOW(6))", match);
        long lowGrade = customer("등급낮음", gn, id("gn01-senior"), true, "C", now.minusDays(150), now.minusDays(400));
        purchase(lowGrade, gn, "WOMEN", "OUTER", 1, 0);
        jdbc.update("INSERT INTO customer_member (customer_id, nickname, relation, created_at) VALUES (?, '첫째', 'CHILD', NOW(6))", lowGrade);
        long wrongItem = customer("품목다름", gn, id("gn01-senior"), true, "A", now.minusDays(150), now.minusDays(400));
        purchase(wrongItem, gn, "WOMEN", "SHOES", 1, 0);
        jdbc.update("INSERT INTO customer_member (customer_id, nickname, relation, created_at) VALUES (?, '첫째', 'CHILD', NOW(6))", wrongItem);
        long voidedItem = customer("취소한품목", gn, id("gn01-senior"), true, "A", now.minusDays(150), now.minusDays(400));
        purchase(voidedItem, gn, "WOMEN", "OUTER", 1, 1); // 기록 취소 → 품목 필터에서 빠진다
        long noChild = customer("자녀없음", gn, id("gn01-senior"), true, "A", now.minusDays(150), now.minusDays(400));
        purchase(noChild, gn, "WOMEN", "OUTER", 1, 0);

        call("store-gn01", "?minGrade=B&staffId=" + id("gn01-senior") + "&inactiveDays=120&target=WOMEN&itemType=OUTER&memberRelation=CHILD")
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(match));
        call("store-gn01", "?minGrade=C&staffId=" + id("gn01-senior") + "&itemType=OUTER&memberRelation=CHILD&promoOnly=false")
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("미방문 일수 경계: 정확히 N일 전이 마지막 방문이면 포함 (이상)")
    void should_includeExactlyInactiveDays() throws Exception {
        long gn = storeId("GN01");
        long junior = id("gn01-junior");
        long exactly = customer("딱90일", gn, junior, true, "D", LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(90).minusSeconds(5),
                now.minusDays(400));
        customer("89일", gn, junior, true, "D", now.minusDays(89), now.minusDays(400));

        call("gn01-junior", "").andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(exactly));
    }

    @Test
    @DisplayName("본사는 storeId가 필수(없으면 400, 없는 매장 404)이고, 직원·매장 계정이 다른 매장을 지정하면 403이다")
    void should_checkStorePermissions() throws Exception {
        long gn = storeId("GN01");
        long hd = storeId("HD01");
        customer("강남가입", gn, null, true, "NORMAL", null, now.minusDays(3));

        call("hq", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("storeId"));
        call("hq", "?storeId=999999").andExpect(status().isNotFound());
        call("hq", "?storeId=" + gn).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        call("hq", "?storeId=" + hd).andExpect(jsonPath("$.totalElements").value(0));
        call("store-gn01", "?storeId=" + hd).andExpect(status().isForbidden());
        call("gn01-junior", "?storeId=" + hd).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("페이지로 나눠 주고 전체 개수는 그대로다, 잘못된 값은 400")
    void should_paginate_andRejectBadValues() throws Exception {
        long gn = storeId("GN01");
        for (int i = 0; i < 3; i++) {
            customer("가입" + i, gn, null, true, "NORMAL", null, now.minusDays(10 + i));
        }

        call("store-gn01", "?size=2&page=1").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        call("store-gn01", "?minGrade=Z").andExpect(status().isBadRequest());
        call("store-gn01", "?inactiveDays=-1").andExpect(status().isBadRequest());
    }
}
