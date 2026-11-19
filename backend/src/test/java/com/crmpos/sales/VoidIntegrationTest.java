package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** 기록 취소를 Controller부터 실제 DB까지 확인한다 (SAL-07, BR-16, ADR-026·030). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
class VoidIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffRepository staffRepository;

    SalesTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new SalesTestKit(mockMvc, jdbc, staffRepository);
        kit.cleanUp();
    }

    @AfterEach
    void tearDown() {
        kit.cleanUp();
    }

    private MockHttpServletRequestBuilder voidRequest(long saleId, String key, Long staffId) {
        MockHttpServletRequestBuilder builder = post("/api/v1/sales/" + saleId + "/void");
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        if (staffId != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{\"staffId\":" + staffId + "}");
        }
        return builder;
    }

    private ResultActions doVoid(long saleId, String loginId) throws Exception {
        Long staff = "hq".equals(loginId) ? null : kit.staffId("gn01-senior");
        return kit.send(kit.as(voidRequest(saleId, SalesTestKit.newKey(), staff), loginId));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void returnQty(long[] sale, int qty) throws Exception {
        kit.send(kit.as(post("/api/v1/sales/" + sale[0] + "/returns").header("Idempotency-Key", SalesTestKit.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":%d}]}".formatted(
                        kit.staffId("gn01-senior"), sale[1], qty)), null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("매장 계정이 오늘 판매를 기록 취소하면 200과 영수증 상세(VOIDED, VOID 내역)이고 라인이 모두 돌려진다")
    void should_voidTodaysSale() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "취소 양말", 3, 100, null);

        doVoid(sale[0], null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.lines[0].returnedQty").value(3))
                .andExpect(jsonPath("$.lines[0].returnedAmount").value(100))
                .andExpect(jsonPath("$.returns[0].kind").value("VOID"))
                .andExpect(jsonPath("$.returns[0].amount").value(100))
                .andExpect(jsonPath("$.returns[0].staffName").value("강남 시니어"));

        Map<String, Object> ret = jdbc.queryForMap("SELECT kind, amount, recorded_by FROM sale_return");
        assertThat(ret.get("kind")).isEqualTo("VOID");
        assertThat(ret.get("recorded_by")).isEqualTo(kit.staffId("store-gn01"));
        assertThat(jdbc.queryForObject("SELECT status FROM sale WHERE id = ?", String.class, sale[0]))
                .isEqualTo("VOIDED");
        // 내역에는 취소로 남고, 하루 판매 수에서는 빠진다
        kit.send(kit.as(get("/api/v1/sales"), null)).andExpect(jsonPath("$.content[0].status").value("VOIDED"));
        kit.send(kit.as(get("/api/v1/sales/daily-summary"), null)).andExpect(jsonPath("$[0].saleCount").value(0));
    }

    @Test
    @DisplayName("기록 취소하면 SALE_RETURNED(kind=VOID) 이벤트가 처음 판매 ID로 묶여 기록된다")
    void should_writeVoidEvent() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "이벤트 양말", 2, 1000, null);

        doVoid(sale[0], null).andExpect(status().isOk());

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT aggregate_id, payload FROM outbox_event WHERE event_type = 'SALE_RETURNED'");
        assertThat(event.get("aggregate_id")).isEqualTo(sale[0]);
        JsonNode payload = kit.mapper.readTree(event.get("payload").toString());
        assertThat(payload.get("kind").asString()).isEqualTo("VOID");
        assertThat(payload.get("lines").get(0).get("qty").asInt()).isEqualTo(2);
        assertThat(payload.get("lines").get(0).get("amount").asLong()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("일부를 반품한 판매는 남은 라인만 기록 취소한다 (남은 금액)")
    void should_voidOnlyRemaining_afterPartialReturn() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "부분 양말", 3, 100, null);
        returnQty(sale, 1);

        doVoid(sale[0], null).andExpect(status().isOk())
                .andExpect(jsonPath("$.returns.length()").value(2))
                .andExpect(jsonPath("$.returns[1].kind").value("VOID"))
                .andExpect(jsonPath("$.returns[1].amount").value(67));
        assertThat(jdbc.queryForObject("SELECT returned_amount FROM sale_line", Long.class)).isEqualTo(100L);
    }

    @Test
    @DisplayName("같은 키로 다시 보내면 200으로 같은 응답이고 한 번만 반영된다, 새 키로 또 취소하면 409 SALE_ALREADY_VOIDED")
    void should_replayAndRejectSecondVoid() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "중복 양말", 1, 1000, null);
        String key = SalesTestKit.newKey();
        long staff = kit.staffId("gn01-senior");

        String first = kit.send(kit.as(voidRequest(sale[0], key, staff), null)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = kit.send(kit.as(voidRequest(sale[0], key, staff), null)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(kit.mapper.readTree(second)).isEqualTo(kit.mapper.readTree(first));
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);
        doVoid(sale[0], null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALE_ALREADY_VOIDED"));
        // 같은 키로 다른 판매를 취소하려 하면 409
        long[] other = kit.createSale(null, "gn01-senior", "다른 양말", 1, 1000, null);
        kit.send(kit.as(voidRequest(other[0], key, staff), null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("전부 반품한 판매는 취소할 라인이 없어 409 RETURN_QTY_EXCEEDED")
    void should_return409_whenNothingRemains() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "전부 반품", 1, 1000, null);
        returnQty(sale, 1);

        doVoid(sale[0], null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_QTY_EXCEEDED"));
    }

    // ---- 권한·잠김

    @Test
    @DisplayName("매장 계정은 지난 날 판매나 오늘 마감 뒤에는 409 SALE_LOCKED, 본사는 언제나 취소한다(처리자는 본사 계정)")
    void should_lockForStore_butNotHq() throws Exception {
        long[] old = kit.createSale(null, "gn01-senior", "지난 양말", 1, 1000, null);
        jdbc.update("UPDATE sale SET sold_at = ? WHERE id = ?", LocalDateTime.now().minusDays(2), old[0]);
        long[] today = kit.createSale(null, "gn01-senior", "오늘 양말", 1, 1000, null);

        doVoid(old[0], null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_LOCKED"));
        kit.send(kit.as(post("/api/v1/day-closes"), null)).andExpect(status().isCreated());
        doVoid(today[0], null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_LOCKED"));

        doVoid(old[0], "hq").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.returns[0].staffName").value("본사 담당"));
        doVoid(today[0], "hq").andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM sale_return WHERE kind = 'VOID'")).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 매장 판매는 403, 직원 계정은 403, 매장 계정이 처리 직원을 안 보내면 400, 없는 판매는 404")
    void should_checkPermissionsAndInput() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "권한 양말", 1, 1000, null);

        kit.send(kit.as(voidRequest(sale[0], SalesTestKit.newKey(), kit.staffId("hd01-senior")), "store-hd01"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        kit.send(kit.as(voidRequest(sale[0], SalesTestKit.newKey(), kit.staffId("gn01-senior")), "gn01-staff"))
                .andExpect(status().isForbidden());
        kit.send(kit.as(voidRequest(sale[0], SalesTestKit.newKey(), null), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
        kit.send(kit.as(voidRequest(sale[0], SalesTestKit.newKey(), kit.staffId("hd01-senior")), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
        kit.send(kit.as(voidRequest(999999L, SalesTestKit.newKey(), kit.staffId("gn01-senior")), null))
                .andExpect(status().isNotFound());
        kit.send(kit.as(voidRequest(sale[0], null, kit.staffId("gn01-senior")), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isZero();
    }

    @Test
    @DisplayName("기록 취소 뒤 그 판매의 반품은 409 SALE_ALREADY_VOIDED이고 고객 구매이력에서 빠진다")
    void should_blockReturnAfterVoid() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "취소 후 양말", 2, 1000, null);
        doVoid(sale[0], null).andExpect(status().isOk());

        kit.send(kit.as(post("/api/v1/sales/" + sale[0] + "/returns").header("Idempotency-Key", SalesTestKit.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":1}]}".formatted(
                        kit.staffId("gn01-senior"), sale[1])), null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_ALREADY_VOIDED"));
    }

    // ---- 동시 요청

    private List<Integer> sendConcurrently(List<MockHttpServletRequestBuilder> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (MockHttpServletRequestBuilder req : requests) {
            futures.add(pool.submit(() -> {
                go.await();
                return mockMvc.perform(req).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();
        return statuses;
    }

    @Test
    @DisplayName("다른 키로 동시에 기록 취소를 4번 보내면 하나만 200이고 나머지는 409이며 취소 행은 하나다")
    void should_voidOnce_whenConcurrent() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "동시 양말", 2, 1000, null);
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            requests.add(kit.as(voidRequest(sale[0], SalesTestKit.newKey(), kit.staffId("gn01-senior")), null));
        }

        List<Integer> statuses = sendConcurrently(requests);

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(3);
        assertThat(count("SELECT COUNT(*) FROM sale_return WHERE kind = 'VOID'")).isEqualTo(1);
    }

    @Test
    @DisplayName("반품과 기록 취소가 동시에 와도 반품된 수량이 판매 수량을 넘지 않는다")
    void should_neverOverReturn_whenReturnAndVoidConcurrent() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "경합 양말", 2, 1000, null);
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        requests.add(kit.as(voidRequest(sale[0], SalesTestKit.newKey(), kit.staffId("gn01-senior")), null));
        for (int i = 0; i < 3; i++) {
            requests.add(kit.as(post("/api/v1/sales/" + sale[0] + "/returns")
                    .header("Idempotency-Key", SalesTestKit.newKey()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":1}]}".formatted(
                            kit.staffId("gn01-senior"), sale[1])), null));
        }

        sendConcurrently(requests);

        Map<String, Object> line = jdbc.queryForMap("SELECT qty, returned_qty, returned_amount, line_amount FROM sale_line");
        assertThat((Integer) line.get("returned_qty")).isLessThanOrEqualTo((Integer) line.get("qty"));
        assertThat((Long) line.get("returned_amount")).isLessThanOrEqualTo((Long) line.get("line_amount"));
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM sale_return", Long.class))
                .isEqualTo((Long) line.get("returned_amount"));
        assertThat(count("SELECT COUNT(*) FROM sale_return WHERE kind = 'VOID'")).isLessThanOrEqualTo(1);
    }
}
