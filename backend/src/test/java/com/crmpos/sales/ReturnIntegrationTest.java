package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.DevCurrentAccountProvider;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 반품을 Controller부터 실제 MariaDB까지 확인한다 (SAL-03, BR-11, ADR-030). 동시 요청을 보려고 트랜잭션 없이 커밋하고
 * 끝나면 지운다. 가상 데이터다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ReturnIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

    private long senior() {
        return staffId("gn01-senior");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** 직접 입력 라인 하나(수량 qty, 결제 금액 amount)로 판매를 만들고 {saleId, lineId}를 돌려준다. */
    private long[] createSale(int qty, long amount) throws Exception {
        String body = """
                {"sellerStaffId":%d,"lines":[{"productName":"양말","itemType":"ACC","size":"FREE","qty":%d,"lineAmount":%d}]}"""
                .formatted(senior(), qty, amount);
        String json = mockMvc.perform(post("/api/v1/sales").header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(json);
        return new long[] {node.get("id").asLong(), node.get("lines").get(0).get("lineId").asLong()};
    }

    private MockHttpServletRequestBuilder returnRequest(long saleId, String key, long lineId, int qty, Long asAccount,
            long staff) {
        MockHttpServletRequestBuilder builder = post("/api/v1/sales/" + saleId + "/returns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":%d}]}".formatted(staff, lineId, qty));
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        if (asAccount != null) {
            builder.header(DevCurrentAccountProvider.HEADER, asAccount);
        }
        return builder;
    }

    private ResultActions doReturn(long[] sale, int qty) throws Exception {
        return mockMvc.perform(returnRequest(sale[0], newKey(), sale[1], qty, null, senior()));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // ---- 환불 금액·상태

    @Test
    @DisplayName("3개 100원을 1개씩 반품하면 환불 33·33·34원이고 상태는 PARTIAL → PARTIAL → RETURNED이다")
    void should_refund33_33_34_andUpdateStatus() throws Exception {
        long[] sale = createSale(3, 100);

        doReturn(sale, 1).andExpect(status().isCreated()).andExpect(jsonPath("$.returnedAmount").value(33))
                .andExpect(jsonPath("$.status").value("PARTIAL_RETURNED"));
        doReturn(sale, 1).andExpect(status().isCreated()).andExpect(jsonPath("$.returnedAmount").value(33))
                .andExpect(jsonPath("$.status").value("PARTIAL_RETURNED"));
        doReturn(sale, 1).andExpect(status().isCreated()).andExpect(jsonPath("$.returnedAmount").value(34))
                .andExpect(jsonPath("$.status").value("RETURNED"));

        Map<String, Object> line = jdbc.queryForMap("SELECT returned_qty, returned_amount FROM sale_line");
        assertThat(line.get("returned_qty")).isEqualTo(3);
        assertThat(line.get("returned_amount")).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT status FROM sale WHERE id = ?", String.class, sale[0]))
                .isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM sale_return", Long.class)).isEqualTo(100L);
        assertThat(count("SELECT COUNT(*) FROM sale_return_line")).isEqualTo(3);
        // 원 판매의 금액·수량은 고치지 않는다
        assertThat(jdbc.queryForObject("SELECT total_amount FROM sale WHERE id = ?", Long.class, sale[0]))
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("반품 행에는 처리한 직원·기록한 계정·종류 RETURN이 남는다")
    void should_storeReturnRow() throws Exception {
        long[] sale = createSale(2, 2000);

        doReturn(sale, 1).andExpect(status().isCreated());

        Map<String, Object> ret = jdbc.queryForMap("SELECT * FROM sale_return");
        assertThat(ret.get("kind")).isEqualTo("RETURN");
        assertThat(ret.get("staff_id")).isEqualTo(senior());
        assertThat(ret.get("recorded_by")).isEqualTo(staffId("store-gn01"));
        assertThat(ret.get("amount")).isEqualTo(1000L);
        assertThat(ret.get("sale_id")).isEqualTo(sale[0]);
        assertThat(ret.get("new_sale_id")).isNull();
    }

    // ---- 규칙

    @Test
    @DisplayName("남은 수량보다 많이 반품하면 409 RETURN_QTY_EXCEEDED이고 아무것도 바뀌지 않는다")
    void should_return409_when_qtyExceeded() throws Exception {
        long[] sale = createSale(3, 100);
        doReturn(sale, 2).andExpect(status().isCreated());

        doReturn(sale, 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_QTY_EXCEEDED"))
                .andExpect(jsonPath("$.details.remainingQty").value(1));

        assertThat(jdbc.queryForObject("SELECT returned_qty FROM sale_line", Integer.class)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 영수증의 줄을 고르면 400 fieldErrors[lines[0].lineId]")
    void should_return400_when_lineOfOtherSale() throws Exception {
        long[] first = createSale(1, 1000);
        long[] second = createSale(1, 2000);

        mockMvc.perform(returnRequest(first[0], newKey(), second[1], 1, null, senior()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].lineId"));

        assertThat(count("SELECT COUNT(*) FROM sale_return")).isZero();
    }

    @Test
    @DisplayName("없는 판매는 404, 본사 계정은 403 STORE_REQUIRED")
    void should_return404And403() throws Exception {
        long[] sale = createSale(1, 1000);

        mockMvc.perform(returnRequest(999999L, newKey(), sale[1], 1, null, senior()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
        mockMvc.perform(returnRequest(sale[0], newKey(), sale[1], 1, staffId("hq"), senior()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_REQUIRED"));
    }

    @Test
    @DisplayName("다른 매장의 판매는 입력이 맞아도 틀려도 403(권한이 먼저)이고, 같은 매장 계정이 다른 매장 직원을 고르면 400")
    void should_checkPermissionFirst_thenInput() throws Exception {
        long[] sale = createSale(1, 1000);
        long hongdaeAccount = staffId("store-hd01");
        long hongdaeStaff = staffId("hd01-senior");

        // 홍대점 계정 + 홍대점 직원(입력은 맞음) → 강남점 판매라서 403
        mockMvc.perform(returnRequest(sale[0], newKey(), sale[1], 1, hongdaeAccount, hongdaeStaff))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        // 홍대점 계정 + 강남점 직원(입력도 틀림) → 그래도 403이 먼저
        mockMvc.perform(returnRequest(sale[0], newKey(), sale[1], 1, hongdaeAccount, senior()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        // 강남점 계정 + 홍대점 직원 → 판매는 자기 매장이지만 직원이 다른 매장이라 400
        mockMvc.perform(returnRequest(sale[0], newKey(), sale[1], 1, null, hongdaeStaff))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));

        assertThat(count("SELECT COUNT(*) FROM sale_return")).isZero();
    }

    @Test
    @DisplayName("기록 취소된 판매는 409 SALE_ALREADY_VOIDED")
    void should_return409_whenVoided() throws Exception {
        long[] sale = createSale(1, 1000);
        jdbc.update("UPDATE sale SET status = 'VOIDED' WHERE id = ?", sale[0]);

        doReturn(sale, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_ALREADY_VOIDED"));
    }

    @Test
    @DisplayName("오늘이 마감되면 409 DAY_CLOSED, 판매한 날이 지났어도 오늘이 열려 있으면 반품할 수 있다")
    void should_checkTodayClose_notSaleDay() throws Exception {
        long[] sale = createSale(2, 1000);
        jdbc.update("UPDATE sale SET sold_at = ? WHERE id = ?", LocalDateTime.now(KST).minusDays(3), sale[0]);
        long storeId = storeRepository.findByCode("GN01").orElseThrow().getId();

        doReturn(sale, 1).andExpect(status().isCreated());

        jdbc.update("INSERT INTO store_day_close (store_id, business_date, closed_by, closed_at) VALUES (?, ?, ?, NOW(6))",
                storeId, LocalDate.now(KST), staffId("store-gn01"));
        doReturn(sale, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);

        jdbc.update("UPDATE store_day_close SET reopened_by = ?, reopened_at = NOW(6)", staffId("hq"));
        doReturn(sale, 1).andExpect(status().isCreated());
    }

    // ---- 중복 요청

    @Test
    @DisplayName("같은 키로 같은 내용을 다시 보내면 200으로 같은 응답이고 반품은 한 번만 반영된다")
    void should_replayWith200() throws Exception {
        long[] sale = createSale(3, 100);
        String key = newKey();

        String first = mockMvc.perform(returnRequest(sale[0], key, sale[1], 1, null, senior()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(returnRequest(sale[0], key, sale[1], 1, null, senior()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
        assertThat(jdbc.queryForObject("SELECT returned_qty FROM sale_line", Integer.class)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE event_type = 'SALE_RETURNED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 다른 내용(수량)을 보내면 409 IDEMPOTENCY_KEY_REUSED")
    void should_return409_whenSameKeyDifferentBody() throws Exception {
        long[] sale = createSale(3, 100);
        String key = newKey();
        mockMvc.perform(returnRequest(sale[0], key, sale[1], 1, null, senior())).andExpect(status().isCreated());

        mockMvc.perform(returnRequest(sale[0], key, sale[1], 2, null, senior()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(jdbc.queryForObject("SELECT returned_qty FROM sale_line", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키가 동시에 5번 와도 반품은 한 번만 저장되고 나머지는 같은 응답 200이다")
    void should_saveOnce_whenSameKeyConcurrent() throws Exception {
        long[] sale = createSale(3, 100);
        String key = newKey();
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            requests.add(returnRequest(sale[0], key, sale[1], 1, null, senior()));
        }

        List<Integer> statuses = sendConcurrently(requests);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT returned_qty FROM sale_line", Integer.class)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);
    }

    // ---- 동시 반품 (ADR-030)

    private List<Integer> sendConcurrently(List<MockHttpServletRequestBuilder> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (MockHttpServletRequestBuilder req : requests) {
            futures.add(pool.submit(() -> {
                go.await();
                MvcResult result = mockMvc.perform(req).andReturn();
                return result.getResponse().getStatus();
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
    @DisplayName("마지막 1개를 다른 키로 동시에 반품하면 하나는 201, 하나는 409 RETURN_QTY_EXCEEDED이다")
    void should_allowOnlyOne_whenLastQtyReturnedConcurrently() throws Exception {
        long[] sale = createSale(1, 1000);
        List<MockHttpServletRequestBuilder> requests = List.of(
                returnRequest(sale[0], newKey(), sale[1], 1, null, senior()),
                returnRequest(sale[0], newKey(), sale[1], 1, null, senior()));

        List<Integer> statuses = sendConcurrently(requests);

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT returned_qty FROM sale_line", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT returned_amount FROM sale_line", Long.class)).isEqualTo(1000L);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);
    }

    @Test
    @DisplayName("3개 100원에 1개씩 6번을 동시에 반품하면 3번만 성공하고 환불 합계는 정확히 100원이다")
    void should_neverReturnMoreThanSold_whenManyConcurrent() throws Exception {
        long[] sale = createSale(3, 100);
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(returnRequest(sale[0], newKey(), sale[1], 1, null, senior()));
        }

        List<Integer> statuses = sendConcurrently(requests);

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT returned_qty FROM sale_line", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT returned_amount FROM sale_line", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM sale_return", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT status FROM sale WHERE id = ?", String.class, sale[0]))
                .isEqualTo("RETURNED");
    }

    // ---- outbox

    @Test
    @DisplayName("반품하면 같은 트랜잭션에서 SALE_RETURNED 이벤트가 처음 판매 ID로 묶여 기록된다")
    void should_writeReturnedEventToOutbox() throws Exception {
        long[] sale = createSale(3, 100);

        doReturn(sale, 1).andExpect(status().isCreated());

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT event_type, aggregate_id, status, payload FROM outbox_event ORDER BY created_at, event_id");
        assertThat(events).hasSize(2);
        Map<String, Object> returned = events.stream().filter(e -> e.get("event_type").equals("SALE_RETURNED"))
                .findFirst().orElseThrow();
        assertThat(returned.get("aggregate_id")).isEqualTo(sale[0]);
        assertThat(returned.get("status")).isEqualTo("PENDING");
        JsonNode payload = mapper.readTree(returned.get("payload").toString());
        assertThat(payload.get("eventType").asString()).isEqualTo("SALE_RETURNED");
        assertThat(payload.get("version").asInt()).isEqualTo(1);
        assertThat(payload.get("saleId").asLong()).isEqualTo(sale[0]);
        assertThat(payload.get("kind").asString()).isEqualTo("RETURN");
        assertThat(payload.get("customerId").isNull()).isTrue();
        assertThat(payload.get("lines").get(0).get("saleLineId").asLong()).isEqualTo(sale[1]);
        assertThat(payload.get("lines").get(0).get("qty").asInt()).isEqualTo(1);
        assertThat(payload.get("lines").get(0).get("amount").asLong()).isEqualTo(33L);
        assertThat(payload.get("returnId").asLong()).isEqualTo(
                jdbc.queryForObject("SELECT id FROM sale_return", Long.class));
    }

    @Test
    @DisplayName("거절된 반품은 반품 행도 이벤트도 남기지 않는다")
    void should_writeNothing_whenRejected() throws Exception {
        long[] sale = createSale(1, 1000);
        int eventsBefore = count("SELECT COUNT(*) FROM outbox_event");

        doReturn(sale, 5).andExpect(status().isConflict());

        assertThat(count("SELECT COUNT(*) FROM outbox_event")).isEqualTo(eventsBefore);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM sale WHERE id = ?", String.class, sale[0]))
                .isEqualTo("COMPLETED");
    }
}
