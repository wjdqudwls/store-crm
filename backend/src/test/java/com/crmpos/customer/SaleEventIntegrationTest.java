package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.event.OutboxMessage;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.event.SaleEventListener;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.sales.event.OutboxPublisher;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 판매 이벤트를 받는 쪽(B3-1)을 실제 MariaDB로 확인한다. 이벤트는 03-api.md 6장 JSON을 그대로 만들어 리스너에 넣고,
 * 마지막 두 테스트는 판매·반품 API → outbox → 발행기 → 고객 모듈까지 이어 본다. 트랜잭션 없이 커밋하고 끝나면 지운다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SaleEventIntegrationTest {

    @Autowired
    SaleEventListener listener;
    @Autowired
    OutboxPublisher publisher;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerSummaryRepository summaryRepository;

    final JsonMapper mapper = JsonMapper.builder().build();
    int phoneSeq = 0;

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
        jdbc.update("DELETE FROM follow_up_task");
        jdbc.update("DELETE FROM processed_event");
        jdbc.update("DELETE FROM consult_note");
        jdbc.update("DELETE FROM customer_purchase");
        jdbc.update("DELETE FROM customer_member");
        jdbc.update("DELETE FROM customer_summary");
        jdbc.update("DELETE FROM customer");
    }

    private long newCustomer() {
        CustomerDraft draft = CustomerDraft.builder().name("이벤트 고객").nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1}).phoneHash(String.format("%064d", ++phoneSeq)).phoneLast4("4821")
                .privacyAgreed(true).age14Confirmed(true).homeStoreId(1L).source(CustomerSource.STORE).build();
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(customer.getId(), 1, now));
        return customer.getId();
    }

    /** lines: {lineId, itemType, qty, amount} */
    private String completedJson(String eventId, long saleId, long rootSaleId, Long originSaleId, Long customerId,
            String kind, String occurredAt, String memo, long[]... lines) {
        StringBuilder sb = new StringBuilder();
        for (long[] l : lines) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append("{\"saleLineId\":%d,\"target\":\"WOMEN\",\"itemType\":\"%s\",\"qty\":%d,\"lineAmount\":%d}"
                    .formatted(l[0], l[1] == 0 ? "OUTER" : "TOP", l[2], l[3]));
        }
        return """
                {"eventId":"%s","eventType":"SALE_COMPLETED","version":1,"occurredAt":"%s","saleId":%d,"receiptNo":"GN01-X",
                 "storeId":1,"customerId":%s,"staffId":7,"recordedBy":3,"kind":"%s","originSaleId":%s,"rootSaleId":%d,
                 "exchange":null,"memo":%s,"lines":[%s]}"""
                .formatted(eventId, occurredAt, saleId, customerId, kind, originSaleId, rootSaleId,
                        memo == null ? "null" : "\"" + memo + "\"", sb);
    }

    private String returnedJson(String eventId, long saleId, Long customerId, String kind, String occurredAt,
            long[]... lines) {
        StringBuilder sb = new StringBuilder();
        for (long[] l : lines) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append("{\"saleLineId\":%d,\"qty\":%d,\"amount\":%d}".formatted(l[0], l[1], l[2]));
        }
        return """
                {"eventId":"%s","eventType":"SALE_RETURNED","version":1,"occurredAt":"%s","saleId":%d,"returnId":1,
                 "kind":"%s","customerId":%s,"lines":[%s]}""".formatted(eventId, occurredAt, saleId, kind, customerId, sb);
    }

    private String send(String type, long root, String json) {
        String eventId = mapper.readTree(json).get("eventId").asString();
        listener.on(new OutboxMessage(eventId, type, root, json));
        return eventId;
    }

    private String completed(long root, String json) {
        return send("SALE_COMPLETED", root, json);
    }

    private String returned(long root, String json) {
        return send("SALE_RETURNED", root, json);
    }

    private String id() {
        return UUID.randomUUID().toString();
    }

    private Map<String, Object> purchase(long lineId) {
        return jdbc.queryForMap("SELECT * FROM customer_purchase WHERE sale_line_id = ?", lineId);
    }

    /** JDBC가 돌려주는 시각(Timestamp)을 LocalDateTime으로 바꾼다. 비어 있으면 null. */
    private static LocalDateTime at(Object value) {
        return value == null ? null : ((java.sql.Timestamp) value).toLocalDateTime();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private Map<String, Object> summary(long customerId) {
        return jdbc.queryForMap("SELECT * FROM customer_summary WHERE customer_id = ?", customerId);
    }

    /** 남은 상품(기록 취소 제외, 반품 제외)의 결제 금액 합 = 그 구매 건의 순구매금액. */
    private long netAmount(long groupId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount - returned_amount), 0) FROM customer_purchase "
                + "WHERE purchase_group_id = ?", Long.class, groupId);
    }

    @Test
    @DisplayName("판매 이벤트를 받으면 구매 기록·응대 메모·방문일이 생기고 처리 기록이 남는다")
    void should_storePurchaseMemoAndVisit_when_saleCompleted() {
        long c = newCustomer();

        String eventId = completed(100, completedJson(id(), 100, 100, null, c, "NORMAL", "2026-11-19T14:03:00+09:00",
                "롱코트 구매", new long[] {1, 0, 1, 774060}, new long[] {2, 1, 2, 217800}));

        assertThat(count("customer_purchase")).isEqualTo(2);
        Map<String, Object> row = purchase(1);
        assertThat(row.get("customer_id")).isEqualTo(c);
        assertThat(row.get("purchase_group_id")).isEqualTo(100L);
        assertThat(at(row.get("sold_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 3));
        assertThat(at(row.get("counted_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 3));
        assertThat(row.get("amount")).isEqualTo(774060L);
        assertThat(row.get("returned_qty")).isEqualTo(0);
        Map<String, Object> note = jdbc.queryForMap("SELECT * FROM consult_note");
        assertThat(note.get("type")).isEqualTo("PURCHASE");
        assertThat(note.get("sale_id")).isEqualTo(100L);
        assertThat(note.get("staff_id")).isEqualTo(7L);
        assertThat(note.get("recorded_by")).isEqualTo(3L);
        assertThat(note.get("content")).isEqualTo("롱코트 구매");
        assertThat(at(summary(c).get("first_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 3));
        assertThat(at(summary(c).get("last_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 3));
        assertThat(jdbc.queryForObject("SELECT event_type FROM processed_event WHERE event_id = ?", String.class,
                eventId)).isEqualTo("SALE_COMPLETED");
    }

    @Test
    @DisplayName("같은 이벤트가 두 번 와도 구매 기록·메모는 한 번만 생긴다")
    void should_applyOnce_when_sameEventTwice() {
        long c = newCustomer();
        String json = completedJson(id(), 100, 100, null, c, "NORMAL", "2026-11-19T14:03:00+09:00", "메모",
                new long[] {1, 0, 1, 1000});
        completed(100, json);
        completed(100, json);
        String ret = returnedJson(id(), 100, c, "RETURN", "2026-11-19T15:00:00+09:00", new long[] {1, 1, 1000});
        returned(100, ret);
        returned(100, ret);

        assertThat(count("customer_purchase")).isEqualTo(1);
        assertThat(count("consult_note")).isEqualTo(1);
        assertThat(count("processed_event")).isEqualTo(2);
        assertThat(purchase(1).get("returned_qty")).isEqualTo(1);
        assertThat(purchase(1).get("returned_amount")).isEqualTo(1000L);
    }

    @Test
    @DisplayName("비회원 판매·반품은 처리 기록만 남기고 건너뛴다")
    void should_skip_when_nonMember() {
        completed(100, completedJson(id(), 100, 100, null, null, "NORMAL", "2026-11-19T14:03:00+09:00", "메모",
                new long[] {1, 0, 1, 1000}));
        returned(100, returnedJson(id(), 100, null, "RETURN", "2026-11-19T15:00:00+09:00", new long[] {1, 1, 1000}));

        assertThat(count("customer_purchase")).isZero();
        assertThat(count("consult_note")).isZero();
        assertThat(count("processed_event")).isEqualTo(2);
    }

    @Test
    @DisplayName("반품하면 나간 수량만 늘고 반품한 구매도 방문으로 남는다")
    void should_keepVisit_when_returned() {
        long c = newCustomer();
        completed(100, completedJson(id(), 100, 100, null, c, "NORMAL", "2026-11-19T14:03:00+09:00", null,
                new long[] {1, 0, 2, 2000}));

        returned(100, returnedJson(id(), 100, c, "RETURN", "2026-11-20T10:00:00+09:00", new long[] {1, 2, 2000}));

        Map<String, Object> row = purchase(1);
        assertThat(row.get("returned_qty")).isEqualTo(2);
        assertThat(row.get("voided_qty")).isEqualTo(0);
        assertThat(at(summary(c).get("last_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 3));
    }

    @Test
    @DisplayName("기록 취소하면 없던 구매라 방문일에서 빠지고, 다른 구매가 있으면 그 날짜로 다시 계산한다")
    void should_dropVisit_when_voided() {
        long c = newCustomer();
        completed(100, completedJson(id(), 100, 100, null, c, "NORMAL", "2026-11-18T10:00:00+09:00", null,
                new long[] {1, 0, 1, 1000}));
        completed(200, completedJson(id(), 200, 200, null, c, "NORMAL", "2026-11-19T14:00:00+09:00", null,
                new long[] {2, 0, 1, 2000}));
        assertThat(at(summary(c).get("first_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 18, 10, 0));
        assertThat(at(summary(c).get("last_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 0));

        returned(200, returnedJson(id(), 200, c, "VOID", "2026-11-19T15:00:00+09:00", new long[] {2, 1, 2000}));

        assertThat(purchase(2).get("voided_qty")).isEqualTo(1);
        assertThat(purchase(2).get("returned_qty")).isEqualTo(1);
        assertThat(at(summary(c).get("last_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 18, 10, 0));

        returned(100, returnedJson(id(), 100, c, "VOID", "2026-11-19T15:10:00+09:00", new long[] {1, 1, 1000}));

        assertThat(at(summary(c).get("first_visit_at"))).isNull();
        assertThat(at(summary(c).get("last_visit_at"))).isNull();
    }

    @Test
    @DisplayName("교환 시나리오: 1·2·3을 사고 1 환불, 2를 4·5로 교환, 3 환불 → 구매 건은 처음 판매 하나, 금액은 4·5만")
    void should_keepOneGroupAndNetAmount_when_exchange() {
        long c = newCustomer();
        completed(100, completedJson(id(), 100, 100, null, c, "NORMAL", "2026-11-10T10:00:00+09:00", null,
                new long[] {1, 0, 1, 100000}, new long[] {2, 1, 1, 200000}, new long[] {3, 0, 1, 300000}));
        returned(100, returnedJson(id(), 100, c, "RETURN", "2026-11-11T10:00:00+09:00", new long[] {1, 1, 100000}));
        // 교환: 2를 돌려받고 새 판매(101, 처음 판매 100)로 4·5를 받는다 (추가 결제 포함 합 450000)
        returned(100, returnedJson(id(), 100, c, "EXCHANGE", "2026-11-12T10:00:00+09:00", new long[] {2, 1, 200000}));
        completed(100, completedJson(id(), 101, 100, 100L, c, "EXCHANGE", "2026-11-12T10:00:00+09:00", null,
                new long[] {4, 0, 1, 250000}, new long[] {5, 1, 1, 200000}));
        returned(100, returnedJson(id(), 100, c, "RETURN", "2026-11-13T10:00:00+09:00", new long[] {3, 1, 300000}));

        List<Long> groups = jdbc.queryForList("SELECT DISTINCT purchase_group_id FROM customer_purchase", Long.class);
        assertThat(groups).containsExactly(100L);
        assertThat(jdbc.queryForList("SELECT DISTINCT counted_at FROM customer_purchase", java.sql.Timestamp.class).stream().map(java.sql.Timestamp::toLocalDateTime).toList())
                .containsExactly(LocalDateTime.of(2026, 11, 10, 10, 0));
        assertThat(at(purchase(4).get("sold_at"))).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));
        assertThat(netAmount(100)).isEqualTo(450000L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_purchase WHERE qty > returned_qty", Long.class))
                .isEqualTo(2); // 4·5가 남아 구매 건(티켓)이 살아 있다
        assertThat(at(summary(c).get("last_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));
    }

    @Test
    @DisplayName("교환한 상품을 다시 교환해도 같은 구매 건이고 처음 판매 시각을 쓴다")
    void should_stayInFirstGroup_when_exchangedAgain() {
        long c = newCustomer();
        completed(100, completedJson(id(), 100, 100, null, c, "NORMAL", "2026-11-10T10:00:00+09:00", null,
                new long[] {1, 0, 1, 100000}));
        returned(100, returnedJson(id(), 100, c, "EXCHANGE", "2026-11-12T10:00:00+09:00", new long[] {1, 1, 100000}));
        completed(100, completedJson(id(), 101, 100, 100L, c, "EXCHANGE", "2026-11-12T10:00:00+09:00", null,
                new long[] {2, 0, 1, 120000}));
        returned(100, returnedJson(id(), 101, c, "EXCHANGE", "2026-11-15T10:00:00+09:00", new long[] {2, 1, 120000}));
        completed(100, completedJson(id(), 102, 100, 101L, c, "EXCHANGE", "2026-11-15T10:00:00+09:00", null,
                new long[] {3, 0, 1, 90000}));

        assertThat(jdbc.queryForList("SELECT DISTINCT purchase_group_id FROM customer_purchase", Long.class))
                .containsExactly(100L);
        assertThat(at(purchase(3).get("counted_at"))).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));
        assertThat(netAmount(100)).isEqualTo(90000L);
    }

    @Test
    @DisplayName("지난 시즌 판매를 교환하면 새 상품의 실적 시각은 처음 판매 날짜(지난 시즌)다")
    void should_countAtFirstSale_when_pastSeasonExchange() {
        long c = newCustomer();
        completed(100, completedJson(id(), 100, 100, null, c, "NORMAL", "2026-05-20T10:00:00+09:00", null,
                new long[] {1, 0, 1, 100000}));
        returned(100, returnedJson(id(), 100, c, "EXCHANGE", "2026-11-19T10:00:00+09:00", new long[] {1, 1, 100000}));
        completed(100, completedJson(id(), 101, 100, 100L, c, "EXCHANGE", "2026-11-19T10:00:00+09:00", null,
                new long[] {2, 0, 1, 130000}));

        assertThat(at(purchase(2).get("sold_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 10, 0));
        assertThat(at(purchase(2).get("counted_at"))).isEqualTo(LocalDateTime.of(2026, 5, 20, 10, 0));
        assertThat(purchase(2).get("purchase_group_id")).isEqualTo(100L);
    }

    @Test
    @DisplayName("고객을 바꾼 정정: A는 기록 취소로 방문일에서 빠지고 B가 같은 시각·구매 메모를 이어받는다")
    void should_moveMemoAndVisit_when_correctionChangesCustomer() {
        long a = newCustomer();
        long b = newCustomer();
        completed(100, completedJson(id(), 100, 100, null, a, "NORMAL", "2026-11-19T09:00:00+09:00", "A의 응대 메모",
                new long[] {1, 0, 1, 1000}));
        returned(100, returnedJson(id(), 100, a, "VOID", "2026-11-19T11:00:00+09:00", new long[] {1, 1, 1000}));

        completed(100, completedJson(id(), 101, 100, 100L, b, "CORRECTION", "2026-11-19T09:00:00+09:00", null,
                new long[] {2, 0, 1, 1000}));

        assertThat(at(summary(a).get("last_visit_at"))).isNull();
        assertThat(at(summary(b).get("last_visit_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 9, 0));
        assertThat(purchase(2).get("customer_id")).isEqualTo(b);
        assertThat(at(purchase(2).get("counted_at"))).isEqualTo(LocalDateTime.of(2026, 11, 19, 9, 0));
        Map<String, Object> note = jdbc.queryForMap("SELECT * FROM consult_note");
        assertThat(count("consult_note")).isEqualTo(1);
        assertThat(note.get("customer_id")).isEqualTo(b);
        assertThat(note.get("sale_id")).isEqualTo(101L);
        assertThat(note.get("content")).isEqualTo("A의 응대 메모");
        assertThat(note.get("staff_id")).isEqualTo(7L);
    }

    @Test
    @DisplayName("받는 쪽이 실패하면 발행기가 되돌려 PENDING으로 두고 처리 기록도 남지 않는다")
    void should_rollbackAndRetry_when_consumerFails() {
        long c = newCustomer();
        String eventId = id();
        String json = returnedJson(eventId, 100, c, "RETURN", "2026-11-19T10:00:00+09:00", new long[] {999, 1, 1000});
        jdbc.update("INSERT INTO outbox_event (event_id, event_type, aggregate_id, payload, status, attempts, created_at) "
                + "VALUES (?, 'SALE_RETURNED', 100, ?, 'PENDING', 0, NOW(6))", eventId, json);

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(count("processed_event")).isZero();
        assertThat(jdbc.queryForMap("SELECT status, attempts FROM outbox_event")).containsEntry("status", "PENDING")
                .containsEntry("attempts", 1);
    }

    @Test
    @DisplayName("판매·반품·기록 취소 API가 만든 이벤트를 발행기가 전달하면 구매 이력이 반영된다")
    void should_reflectApiSales_when_publisherDelivers() throws Exception {
        long c = newCustomer();
        long seller = jdbc.queryForObject("SELECT id FROM staff WHERE login_id = 'gn01-senior'", Long.class);
        String body = """
                {"sellerStaffId":%d,"customerId":%d,"memo":"스키 바지 관심","lines":[{"productName":"머플러","itemType":"ACC","size":"FREE","qty":2,"lineAmount":18000}]}"""
                .formatted(seller, c);
        String json = mockMvc.perform(post("/api/v1/sales").header("Idempotency-Key", id())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode sale = mapper.readTree(json);
        long saleId = sale.get("id").asLong();
        long lineId = sale.get("lines").get(0).get("lineId").asLong();

        assertThat(publisher.publishPending()).isEqualTo(1);

        assertThat(purchase(lineId).get("purchase_group_id")).isEqualTo(saleId);
        assertThat(purchase(lineId).get("item_type")).isEqualTo("ACC");
        assertThat(purchase(lineId).get("amount")).isEqualTo(18000L);
        assertThat(jdbc.queryForObject("SELECT content FROM consult_note", String.class)).isEqualTo("스키 바지 관심");
        assertThat(at(summary(c).get("last_visit_at"))).isNotNull();

        mockMvc.perform(post("/api/v1/sales/" + saleId + "/returns").header("Idempotency-Key", id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":1}]}".formatted(seller, lineId)))
                .andExpect(status().isCreated());

        assertThat(publisher.publishPending()).isEqualTo(1);
        assertThat(purchase(lineId).get("returned_qty")).isEqualTo(1);
        assertThat(purchase(lineId).get("returned_amount")).isEqualTo(9000L);
        assertThat(count("processed_event")).isEqualTo(2);
    }
}
