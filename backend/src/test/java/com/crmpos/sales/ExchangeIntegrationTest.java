package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.sales.event.OutboxPublisher;
import com.crmpos.support.IntegrationTest;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;

/** 교환을 Controller부터 DB·고객 반영까지 확인한다 (SAL-06, BR-11·17, ADR-006·022·030). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
class ExchangeIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    OutboxPublisher publisher;

    SalesTestKit kit;

    @BeforeEach
    void setUp() {
        kit = new SalesTestKit(mockMvc, jdbc, staffRepository);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM processed_event");
        jdbc.update("DELETE FROM consult_note");
        jdbc.update("DELETE FROM customer_purchase");
        kit.cleanUp();
    }

    private long customer(String phone) throws Exception {
        String body = """
                {"name":"김가온","phone":"%s","agreements":{"privacy":true,"age14":true}}""".formatted(phone);
        String json = kit.send(kit.as(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body), null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return kit.mapper.readTree(json).get("id").asLong();
    }

    private String body(long saleLineId, int returnQty, int newQty, long newAmount) {
        return ("{\"staffId\":%d,\"returnLines\":[{\"lineId\":%d,\"qty\":%d}],"
                + "\"newLines\":[{\"productName\":\"새 상품\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":%d,\"lineAmount\":%d}]}")
                .formatted(kit.staffId("gn01-junior"), saleLineId, returnQty, newQty, newAmount);
    }

    private ResultActions exchange(long saleId, String loginId, String key, String json) throws Exception {
        return kit.send(kit.as(post("/api/v1/sales/" + saleId + "/exchanges").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json), loginId));
    }

    private JsonNode exchangeOk(long saleId, String json) throws Exception {
        return kit.mapper.readTree(exchange(saleId, null, SalesTestKit.newKey(), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private void publishAll() {
        while (publisher.publishPending() > 0) {
            // 앞 이벤트가 끝나야 다음이 가므로 남는 것이 없을 때까지 돌린다
        }
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("교환하면 201: 원 판매는 부분 반품, 새 판매는 EXCHANGE로 원·처음 판매를 잇고 extraPaid는 차액이다")
    void should_exchange_andReportExtraPaid() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 2, 10000, null);

        JsonNode res = exchangeOk(sale[0], body(sale[1], 1, 1, 7000));

        assertThat(res.get("returnedAmount").asLong()).isEqualTo(5000);
        assertThat(res.get("extraPaid").asLong()).isEqualTo(2000);
        long newId = res.get("newSale").get("id").asLong();
        assertThat(res.get("newSale").get("kind").asString()).isEqualTo("EXCHANGE");
        assertThat(res.get("newSale").get("sellerStaffId").asLong()).isEqualTo(kit.staffId("gn01-junior"));
        assertThat(jdbc.queryForObject("SELECT status FROM sale WHERE id = ?", String.class, sale[0]))
                .isEqualTo("PARTIAL_RETURNED");
        Map<String, Object> row = jdbc.queryForMap("SELECT origin_sale_id, root_sale_id FROM sale WHERE id = ?", newId);
        assertThat(row.get("origin_sale_id")).isEqualTo(sale[0]);
        assertThat(row.get("root_sale_id")).isEqualTo(sale[0]);
        Map<String, Object> ret = jdbc.queryForMap("SELECT kind, new_sale_id, amount FROM sale_return");
        assertThat(ret.get("kind")).isEqualTo("EXCHANGE");
        assertThat(ret.get("new_sale_id")).isEqualTo(newId);
    }

    @Test
    @DisplayName("싼 상품으로 바꾸면 extraPaid가 음수(차액 환불)이고, 전부 돌려주면 원 판매는 RETURNED")
    void should_refundDifference_whenCheaper() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 10000, null);

        JsonNode res = exchangeOk(sale[0], body(sale[1], 1, 1, 4000));

        assertThat(res.get("extraPaid").asLong()).isEqualTo(-6000);
        assertThat(jdbc.queryForObject("SELECT status FROM sale WHERE id = ?", String.class, sale[0]))
                .isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("이벤트 처리 뒤: 같은 구매 건에 금액만 바뀌고, 전부 교환해도 후속 연락 할 일은 남는다 (BR-14·17)")
    void should_keepGroupAndFollowUps_afterFullExchange() throws Exception {
        long customerId = customer("010-0000-8001");
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 10000, customerId);
        publishAll();
        int open = count("SELECT COUNT(*) FROM follow_up_task WHERE status = 'OPEN'");
        assertThat(open).isEqualTo(3);

        JsonNode res = exchangeOk(sale[0], body(sale[1], 1, 1, 13000));
        publishAll();

        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE status <> 'PUBLISHED'")).isZero();
        assertThat(count("SELECT COUNT(DISTINCT purchase_group_id) FROM customer_purchase WHERE customer_id = ?", customerId)).isEqualTo(1);
        long newId = res.get("newSale").get("id").asLong();
        Map<String, Object> bought = jdbc.queryForMap(
                "SELECT purchase_group_id, counted_at, amount FROM customer_purchase WHERE sale_id = ?", newId);
        assertThat(bought.get("purchase_group_id")).isEqualTo(sale[0]);
        assertThat(bought.get("amount")).isEqualTo(13000L);
        assertThat(bought.get("counted_at")).isEqualTo(jdbc.queryForObject(
                "SELECT counted_at FROM customer_purchase WHERE sale_id = ?", Object.class, sale[0]));
        assertThat(count("SELECT COUNT(*) FROM follow_up_task WHERE status = 'OPEN'")).isEqualTo(3);
    }

    @Test
    @DisplayName("교환한 상품을 다시 교환해도 처음 판매를 잇고, 그 구매 건을 전부 반품하면 할 일이 취소된다")
    void should_keepRoot_whenExchangedAgain_andCancelWhenAllReturned() throws Exception {
        long customerId = customer("010-0000-8002");
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 10000, customerId);
        JsonNode first = exchangeOk(sale[0], body(sale[1], 1, 1, 10000));
        long second = first.get("newSale").get("id").asLong();
        long secondLine = first.get("newSale").get("lines").get(0).get("lineId").asLong();

        JsonNode again = exchangeOk(second, body(secondLine, 1, 1, 9000));
        long third = again.get("newSale").get("id").asLong();

        assertThat(jdbc.queryForObject("SELECT root_sale_id FROM sale WHERE id = ?", Long.class, third)).isEqualTo(sale[0]);
        assertThat(jdbc.queryForObject("SELECT origin_sale_id FROM sale WHERE id = ?", Long.class, third)).isEqualTo(second);
        publishAll();
        assertThat(count("SELECT COUNT(*) FROM follow_up_task WHERE status = 'OPEN'")).isEqualTo(3);

        long thirdLine = again.get("newSale").get("lines").get(0).get("lineId").asLong();
        kit.send(kit.as(post("/api/v1/sales/" + third + "/returns").header("Idempotency-Key", SalesTestKit.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":1}]}".formatted(kit.staffId("gn01-senior"), thirdLine)), null))
                .andExpect(status().isCreated());
        publishAll();
        assertThat(count("SELECT COUNT(*) FROM follow_up_task WHERE status = 'OPEN'")).isZero();
    }

    @Test
    @DisplayName("적용 등급은 원 판매의 값을 그대로 쓴다")
    void should_keepOriginalAppliedGrade() throws Exception {
        long customerId = customer("010-0000-8003");
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 10000, customerId);
        jdbc.update("UPDATE customer_summary SET current_grade = 'A' WHERE customer_id = ?", customerId);

        JsonNode res = exchangeOk(sale[0], body(sale[1], 1, 1, 10000));

        assertThat(res.get("newSale").get("appliedGrade").asString()).isEqualTo("NORMAL");
        assertThat(res.get("newSale").get("customerId").asLong()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("같은 키로 다시 보내면 200으로 같은 응답이고 한 번만 반영되며, 다른 내용·반품에 쓴 키는 409")
    void should_replay_whenSameKey() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 3, 3000, null);
        String key = SalesTestKit.newKey();
        String json = body(sale[1], 1, 1, 1500);
        String first = exchange(sale[0], null, key, json).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();

        String second = exchange(sale[0], null, key, json).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(kit.mapper.readTree(second)).isEqualTo(kit.mapper.readTree(first));
        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(2);
        exchange(sale[0], null, key, body(sale[1], 2, 1, 1500)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        String saleKey = jdbc.queryForObject("SELECT idempotency_key FROM sale WHERE id = ?", String.class, sale[0]);
        exchange(sale[0], null, saleKey, json).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("남은 수량 초과·취소된 판매는 409, 마감한 날은 DAY_CLOSED이고 아무것도 안 바뀐다")
    void should_return409() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        exchange(sale[0], null, SalesTestKit.newKey(), body(sale[1], 2, 1, 1000))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETURN_QTY_EXCEEDED"));
        kit.send(kit.as(post("/api/v1/day-closes"), null)).andExpect(status().is2xxSuccessful());
        exchange(sale[0], null, SalesTestKit.newKey(), body(sale[1], 1, 1, 1000))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isZero();
    }

    @Test
    @DisplayName("권한·입력: 다른 매장 403, 본사 403, 없는 판매 404, 새 상품 없음·비회원 착용자·남의 줄은 400")
    void should_validateRequest() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        String ok = body(sale[1], 1, 1, 1000);

        exchange(sale[0], "store-hd01", SalesTestKit.newKey(), ok).andExpect(status().isForbidden());
        exchange(sale[0], "hq", SalesTestKit.newKey(), ok).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_REQUIRED"));
        exchange(999999, null, SalesTestKit.newKey(), ok).andExpect(status().isNotFound());
        exchange(sale[0], null, SalesTestKit.newKey(),
                "{\"staffId\":%d,\"returnLines\":[{\"lineId\":%d,\"qty\":1}],\"newLines\":[]}"
                        .formatted(kit.staffId("gn01-junior"), sale[1])).andExpect(status().isBadRequest());
        exchange(sale[0], null, SalesTestKit.newKey(), ok.replace("\"qty\":1,\"lineAmount\"", "\"memberId\":1,\"qty\":1,\"lineAmount\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("newLines[0].memberId"));
        exchange(sale[0], null, SalesTestKit.newKey(), body(999999, 1, 1, 1000)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("returnLines[0].lineId"));
        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(1);
    }
}
