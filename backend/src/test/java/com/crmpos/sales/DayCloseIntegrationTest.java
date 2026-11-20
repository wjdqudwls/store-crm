package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.support.TestAuth;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 하루 마감·다시 열기·다시 마감과 그 뒤 판매·반품 잠금을 실제 DB로 확인한다 (SAL-08, BR-16). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
class DayCloseIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.now(KST);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;

    final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM sale_return_line");
        jdbc.update("DELETE FROM sale_return");
        jdbc.update("DELETE FROM sale_line");
        jdbc.update("UPDATE sale SET root_sale_id = NULL, origin_sale_id = NULL");
        jdbc.update("DELETE FROM sale");
        jdbc.update("DELETE FROM receipt_sequence");
        jdbc.update("DELETE FROM store_day_close");
    }

    private long staffId(String loginId) {
        return staffRepository.findAll().stream().filter(s -> s.getLoginId().equals(loginId)).findFirst()
                .orElseThrow().getId();
    }

    private long gangnam() {
        return storeRepository.findByCode("GN01").orElseThrow().getId();
    }

    private ResultActions as(MockHttpServletRequestBuilder builder, String loginId) throws Exception {
        if (loginId != null) {
            builder.header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(staffId(loginId)));
        }
        return mockMvc.perform(builder);
    }

    private ResultActions closeToday(String loginId) throws Exception {
        return as(post("/api/v1/day-closes"), loginId);
    }

    private ResultActions reopen(long storeId, LocalDate date, String loginId) throws Exception {
        return as(delete("/api/v1/stores/" + storeId + "/day-closes/" + date), loginId);
    }

    private ResultActions sell(String storeAccount, String seller) throws Exception {
        String body = """
                {"sellerStaffId":%d,"lines":[{"productName":"양말","itemType":"ACC","size":"FREE","qty":2,"lineAmount":2000}]}"""
                .formatted(staffId(seller));
        return as(post("/api/v1/sales").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body), storeAccount);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // ---- 마감

    @Test
    @DisplayName("매장 계정이 마감하면 201과 마감 현황 항목, 다시 부르면 200으로 같은 응답이다")
    void should_closeThenReturnSameWith200() throws Exception {
        String first = closeToday(null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(TODAY.toString()))
                .andExpect(jsonPath("$.closedByName").value("강남점 매장"))
                .andExpect(jsonPath("$.reopenedAt").value((Object) null))
                .andExpect(jsonPath("$.reopenedByName").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        String second = closeToday(null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
        assertThat(count("SELECT COUNT(*) FROM store_day_close")).isEqualTo(1);
    }

    @Test
    @DisplayName("매장 계정이 아니면 마감할 수 없다 (403 STORE_REQUIRED)")
    void should_return403_whenNotStoreAccount() throws Exception {
        closeToday("hq").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_REQUIRED"));
        closeToday("gn01-senior").andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM store_day_close")).isZero();
    }

    @Test
    @DisplayName("마감하면 그 매장의 판매·반품이 409 DAY_CLOSED이고, 다른 매장은 영향이 없다")
    void should_lockSalesAndReturns_afterClose() throws Exception {
        sell(null, "gn01-senior").andExpect(status().isCreated());
        long saleId = jdbc.queryForObject("SELECT id FROM sale", Long.class);
        long lineId = jdbc.queryForObject("SELECT id FROM sale_line", Long.class);

        closeToday(null).andExpect(status().isCreated());

        sell(null, "gn01-senior").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        as(post("/api/v1/sales/" + saleId + "/returns").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":1}]}".formatted(staffId("gn01-senior"),
                        lineId)), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        sell("store-hd01", "hd01-senior").andExpect(status().isCreated());
        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(2);
    }

    @Test
    @DisplayName("어제 마감은 오늘 판매에 영향이 없다 (날짜가 바뀌면 자동으로 열린 날이다)")
    void should_notLockToday_whenYesterdayClosed() throws Exception {
        jdbc.update("INSERT INTO store_day_close (store_id, business_date, closed_by, closed_at) VALUES (?, ?, ?, NOW(6))",
                gangnam(), TODAY.minusDays(1), staffId("store-gn01"));

        sell(null, "gn01-senior").andExpect(status().isCreated());
    }

    // ---- 다시 열기·다시 마감

    @Test
    @DisplayName("본사가 오늘 마감을 다시 열면 다시 기록할 수 있고, 다시 마감하면 다시 잠긴다")
    void should_reopenThenCloseAgain() throws Exception {
        closeToday(null).andExpect(status().isCreated());
        String closedAtFirst = mapper.readTree(
                as(get("/api/v1/stores/" + gangnam() + "/day-closes"), "hq").andReturn().getResponse()
                        .getContentAsString()).get(0).get("closedAt").asString();

        reopen(gangnam(), TODAY, "hq").andExpect(status().isOk())
                .andExpect(jsonPath("$.reopenedByName").value("본사 담당"))
                .andExpect(jsonPath("$.reopenedAt").isNotEmpty());
        sell(null, "gn01-senior").andExpect(status().isCreated());

        closeToday(null).andExpect(status().isOk())
                .andExpect(jsonPath("$.reopenedAt").value((Object) null))
                .andExpect(jsonPath("$.reopenedByName").value((Object) null));
        sell(null, "gn01-senior").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));

        JsonNode item = mapper.readTree(as(get("/api/v1/stores/" + gangnam() + "/day-closes"), "hq").andReturn()
                .getResponse().getContentAsString()).get(0);
        assertThat(item.get("closedAt").asString()).isNotEqualTo(closedAtFirst);
        assertThat(count("SELECT COUNT(*) FROM store_day_close")).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 열린 날을 또 열어도 200이고 처음 연 기록이 그대로다")
    void should_keepFirstReopen_whenReopenTwice() throws Exception {
        closeToday(null);
        String first = reopen(gangnam(), TODAY, "hq").andReturn().getResponse().getContentAsString();

        String second = reopen(gangnam(), TODAY, "hq").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
    }

    @Test
    @DisplayName("다시 열기: 매장·직원 계정은 403, 마감 기록이 없으면 404, 오늘이 아니면 400, 없는 매장은 404")
    void should_checkReopenRules() throws Exception {
        closeToday(null);

        reopen(gangnam(), TODAY, "store-gn01").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        reopen(gangnam(), TODAY, "gn01-senior").andExpect(status().isForbidden());
        reopen(gangnam(), TODAY.minusDays(1), "hq").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("date"));
        reopen(storeRepository.findByCode("HD01").orElseThrow().getId(), TODAY, "hq").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DAY_CLOSE_NOT_FOUND"));
        reopen(999999L, TODAY, "hq").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        as(delete("/api/v1/stores/" + gangnam() + "/day-closes/not-a-date"), "hq").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT reopened_at FROM store_day_close", java.sql.Timestamp.class)).isNull();
    }

    // ---- 동시 마감

    @Test
    @DisplayName("같은 날을 동시에 5번 마감해도 행은 하나이고 하나만 201, 나머지는 200이다")
    void should_haveOneRow_whenClosedConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return mockMvc.perform(post("/api/v1/day-closes")).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(4);
        assertThat(count("SELECT COUNT(*) FROM store_day_close")).isEqualTo(1);
    }

    // ---- 마감 현황

    @Test
    @DisplayName("마감 현황은 마감 행이 있는 날만 최신순, 기간을 안 주면 최근 30일이고 from·to로 줄일 수 있다")
    void should_listClosedDays_newestFirst() throws Exception {
        for (int days : new int[] {1, 5, 40}) {
            jdbc.update("INSERT INTO store_day_close (store_id, business_date, closed_by, closed_at) VALUES (?, ?, ?, NOW(6))",
                    gangnam(), TODAY.minusDays(days), staffId("store-gn01"));
        }
        String url = "/api/v1/stores/" + gangnam() + "/day-closes";

        as(get(url), null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$[1].date").value(TODAY.minusDays(5).toString()))
                .andExpect(jsonPath("$[0].closedByName").value("강남점 매장"));
        as(get(url).param("from", TODAY.minusDays(60).toString()), "hq").andExpect(jsonPath("$.length()").value(3));
        as(get(url).param("from", TODAY.minusDays(3).toString()).param("to", TODAY.toString()), "hq")
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("마감 현황 권한·검증: 다른 매장 계정 403, 직원 403, 없는 매장 404, 기간이 거꾸로면 400")
    void should_checkListRules() throws Exception {
        String url = "/api/v1/stores/" + gangnam() + "/day-closes";

        as(get(url), "store-hd01").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        as(get(url), "gn01-senior").andExpect(status().isForbidden());
        as(get("/api/v1/stores/999999/day-closes"), "hq").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        as(get(url).param("from", TODAY.toString()).param("to", TODAY.minusDays(1).toString()), "hq")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
        as(get(url).param("from", TODAY.minusDays(500).toString()), "hq").andExpect(status().isBadRequest());
    }
}
