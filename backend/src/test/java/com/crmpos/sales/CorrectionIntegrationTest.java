package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.sales.event.OutboxPublisher;
import com.crmpos.support.IntegrationTest;
import java.util.List;
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

/** 판매 정정을 Controller부터 DB·고객 반영까지 확인한다 (SAL-07, BR-16, ADR-026). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
class CorrectionIntegrationTest {

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

    private String body(Long staffId, Long customerId, String product, int qty, long amount) {
        return "{%s%s\"lines\":[{\"productName\":\"%s\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":%d,\"lineAmount\":%d}]}"
                .formatted(staffId == null ? "" : "\"staffId\":" + staffId + ",",
                        customerId == null ? "" : "\"customerId\":" + customerId + ",", product, qty, amount);
    }

    private ResultActions correct(long saleId, String loginId, String key, String json) throws Exception {
        return kit.send(kit.as(put("/api/v1/sales/" + saleId).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json), loginId));
    }

    private ResultActions correctByStore(long saleId, Long customerId) throws Exception {
        return correct(saleId, null, SalesTestKit.newKey(),
                body(kit.staffId("gn01-senior"), customerId, "고친 상품", 1, 5000));
    }

    private void publishAll() {
        while (publisher.publishPending() > 0) {
            // 같은 판매의 이벤트는 앞이 끝나야 다음이 가므로 남는 것이 없을 때까지 돌린다
        }
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("정정하면 201이고 원 판매는 VOIDED, 새 판매(CORRECTION)는 원 시각·판매 사원·처음 판매를 잇는다")
    void should_createCorrectionSale_andVoidOriginal() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 2, 4000, null);

        String json = correct(sale[0], null, SalesTestKit.newKey(),
                body(kit.staffId("gn01-junior"), null, "고친 상품", 1, 5000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CORRECTION"))
                .andExpect(jsonPath("$.originSaleId").value(sale[0]))
                .andExpect(jsonPath("$.totalAmount").value(5000))
                .andExpect(jsonPath("$.sellerStaffId").value(kit.staffId("gn01-senior")))
                .andReturn().getResponse().getContentAsString();
        long newId = kit.mapper.readTree(json).get("id").asLong();

        Map<String, Object> original = jdbc.queryForMap("SELECT status, sold_at, receipt_no FROM sale WHERE id = ?", sale[0]);
        Map<String, Object> replacement = jdbc.queryForMap(
                "SELECT status, sold_at, receipt_no, kind, origin_sale_id, root_sale_id FROM sale WHERE id = ?", newId);
        assertThat(original.get("status")).isEqualTo("VOIDED");
        assertThat(replacement.get("sold_at")).isEqualTo(original.get("sold_at"));
        assertThat(replacement.get("receipt_no")).isNotEqualTo(original.get("receipt_no"));
        assertThat(replacement.get("origin_sale_id")).isEqualTo(sale[0]);
        assertThat(replacement.get("root_sale_id")).isEqualTo(sale[0]);
        Map<String, Object> ret = jdbc.queryForMap("SELECT kind, new_sale_id, staff_id, amount FROM sale_return");
        assertThat(ret.get("kind")).isEqualTo("VOID");
        assertThat(ret.get("new_sale_id")).isEqualTo(newId);
        assertThat(ret.get("staff_id")).isEqualTo(kit.staffId("gn01-junior"));
        assertThat(ret.get("amount")).isEqualTo(4000L);
    }

    @Test
    @DisplayName("영수증 상세 줄에 target이 있어, 직접 입력 줄을 정정할 때 그대로 보내면 대상이 유지된다 (고객 구매 기록 포함)")
    void should_keepTarget_whenCorrectingDirectLine() throws Exception {
        long customerId = customer("010-0000-7009");
        String saleBody = "{\"sellerStaffId\":%d,\"customerId\":%d,\"lines\":[{\"productName\":\"원피스\",\"itemType\":\"TOP\",\"target\":\"WOMEN\",\"size\":\"M\",\"qty\":1,\"lineAmount\":3000},{\"productName\":\"양말\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":1,\"lineAmount\":1000}]}"
                .formatted(kit.staffId("gn01-senior"), customerId);
        long saleId = kit.mapper.readTree(kit.send(kit.as(post("/api/v1/sales").header("Idempotency-Key", SalesTestKit.newKey())
                        .contentType(MediaType.APPLICATION_JSON).content(saleBody), null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        publishAll();
        JsonNode lines = kit.mapper.readTree(kit.send(kit.as(get("/api/v1/sales/" + saleId), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].target").value("WOMEN"))
                .andExpect(jsonPath("$.lines[1].target").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString()).get("lines");

        // 화면의 정정 창처럼 상세 줄의 값을 그대로 보낸다
        String target = lines.get(0).get("target").asString();
        String correction = "{\"staffId\":%d,\"customerId\":%d,\"lines\":[{\"productName\":\"원피스\",\"itemType\":\"TOP\",\"target\":\"%s\",\"size\":\"L\",\"qty\":1,\"lineAmount\":3000}]}"
                .formatted(kit.staffId("gn01-senior"), customerId, target);
        long newId = kit.mapper.readTree(correct(saleId, null, SalesTestKit.newKey(), correction)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        publishAll();

        kit.send(kit.as(get("/api/v1/sales/" + newId), null)).andExpect(jsonPath("$.lines[0].target").value("WOMEN"));
        assertThat(jdbc.queryForObject("SELECT target FROM customer_purchase WHERE sale_id = ?", String.class, newId))
                .isEqualTo("WOMEN");
    }

    @Test
    @DisplayName("정정 뒤 이벤트를 처리하면 고객 구매 기록이 새 내용이고 옛 줄은 취소, 응대 메모는 정정 판매로 옮겨진다")
    void should_reflectNewContent_toCustomer() throws Exception {
        long customerId = customer("010-0000-7001");
        String saleBody = "{\"sellerStaffId\":%d,\"customerId\":%d,\"memo\":\"가상 메모\",\"lines\":[{\"productName\":\"원래\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":2,\"lineAmount\":4000}]}"
                .formatted(kit.staffId("gn01-senior"), customerId);
        long saleId = kit.mapper.readTree(kit.send(kit.as(post("/api/v1/sales").header("Idempotency-Key", SalesTestKit.newKey())
                        .contentType(MediaType.APPLICATION_JSON).content(saleBody), null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        publishAll();

        long newId = kit.mapper.readTree(correctByStore(saleId, customerId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        publishAll();

        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE status <> 'PUBLISHED'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM customer_purchase WHERE sale_id = ? AND voided_qty = qty", saleId)).isEqualTo(1);
        Map<String, Object> bought = jdbc.queryForMap(
                "SELECT qty, amount, voided_qty, purchase_group_id FROM customer_purchase WHERE sale_id = ?", newId);
        assertThat(bought.get("qty")).isEqualTo(1);
        assertThat(bought.get("amount")).isEqualTo(5000L);
        assertThat(bought.get("voided_qty")).isEqualTo(0);
        assertThat(bought.get("purchase_group_id")).isEqualTo(saleId);
        assertThat(count("SELECT COUNT(*) FROM consult_note WHERE sale_id = ? AND customer_id = ?", newId, customerId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM customer_summary WHERE customer_id = ? AND last_visit_at IS NOT NULL", customerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("고객을 바꾸면 새 고객에게 구매·메모가 가고 원 고객 것은 취소되며, 적용 등급은 새 고객의 지금 등급이다")
    void should_moveToNewCustomer() throws Exception {
        long first = customer("010-0000-7002");
        long second = customer("010-0000-7003");
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, first);
        publishAll();
        jdbc.update("UPDATE customer_summary SET current_grade = 'B' WHERE customer_id = ?", second);

        correctByStore(sale[0], second).andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(second))
                .andExpect(jsonPath("$.appliedGrade").value("B"));
        publishAll();

        assertThat(count("SELECT COUNT(*) FROM customer_purchase WHERE customer_id = ? AND voided_qty = qty", first)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM customer_purchase WHERE customer_id = ? AND voided_qty = 0", second)).isEqualTo(1);
    }

    @Test
    @DisplayName("고객이 그대로면 적용 등급은 원 판매의 값을 유지한다")
    void should_keepAppliedGrade_whenSameCustomer() throws Exception {
        long customerId = customer("010-0000-7004");
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, customerId);
        jdbc.update("UPDATE customer_summary SET current_grade = 'A' WHERE customer_id = ?", customerId);

        correctByStore(sale[0], customerId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedGrade").value("NORMAL"));
    }

    @Test
    @DisplayName("같은 키로 다시 보내면 200으로 같은 판매이고 한 번만 반영되며, 같은 키에 다른 내용은 409")
    void should_replay_whenSameKey() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        String key = SalesTestKit.newKey();
        String json = body(kit.staffId("gn01-senior"), null, "고친 상품", 1, 5000);
        String first = correct(sale[0], null, key, json).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();

        String second = correct(sale[0], null, key, json).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat(kit.mapper.readTree(second).get("id")).isEqualTo(kit.mapper.readTree(first).get("id"));
        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isEqualTo(1);
        correct(sale[0], null, key, body(kit.staffId("gn01-senior"), null, "또 다른", 1, 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("판매 기록에 쓴 키를 정정에 다시 쓰면 409")
    void should_return409_whenKeyUsedBySale() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        String used = jdbc.queryForObject("SELECT idempotency_key FROM sale WHERE id = ?", String.class, sale[0]);

        correct(sale[0], null, used, body(kit.staffId("gn01-senior"), null, "고친", 1, 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("매장 계정은 마감 뒤에는 409 SALE_LOCKED, 본사는 마감된 지난 날 판매도 고친다")
    void should_lockForStore_butAllowHq() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        kit.send(kit.as(post("/api/v1/day-closes"), null)).andExpect(status().is2xxSuccessful());

        correctByStore(sale[0], null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_LOCKED"));

        jdbc.update("UPDATE sale SET sold_at = DATE_SUB(sold_at, INTERVAL 1 DAY) WHERE id = ?", sale[0]);
        correctByStore(sale[0], null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_LOCKED"));
        long newId = kit.mapper.readTree(correct(sale[0], "hq", SalesTestKit.newKey(),
                body(null, null, "본사 정정", 1, 7000)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT recorded_by FROM sale WHERE id = ?", Long.class, newId))
                .isEqualTo(kit.staffId("hq"));
        assertThat(jdbc.queryForObject("SELECT DATE(sold_at) < CURDATE() FROM sale WHERE id = ?", Boolean.class, newId)).isTrue();
    }

    @Test
    @DisplayName("이미 취소·정정한 판매는 409 SALE_ALREADY_VOIDED, 전부 반품한 판매는 409 RETURN_QTY_EXCEEDED")
    void should_return409_whenNothingToCorrect() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        correctByStore(sale[0], null).andExpect(status().isCreated());
        correctByStore(sale[0], null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALE_ALREADY_VOIDED"));

        long[] returned = kit.createSale(null, "gn01-senior", "반품 상품", 1, 1000, null);
        kit.send(kit.as(post("/api/v1/sales/" + returned[0] + "/returns").header("Idempotency-Key", SalesTestKit.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":1}]}".formatted(kit.staffId("gn01-senior"), returned[1])), null))
                .andExpect(status().isCreated());
        correctByStore(returned[0], null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETURN_QTY_EXCEEDED"));
    }

    @Test
    @DisplayName("권한·입력: 다른 매장 403, 직원 계정 403, 없는 판매 404, 처리 직원 없음·라인 없음·키 없음 400, 실패하면 아무것도 안 바뀐다")
    void should_validateRequest() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        String ok = body(kit.staffId("gn01-senior"), null, "고친", 1, 1);

        correct(sale[0], "store-hd01", SalesTestKit.newKey(), ok).andExpect(status().isForbidden());
        correct(sale[0], "gn01-senior", SalesTestKit.newKey(), ok).andExpect(status().isForbidden());
        correct(999999, null, SalesTestKit.newKey(), ok).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
        correct(sale[0], null, SalesTestKit.newKey(), body(null, null, "고친", 1, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
        correct(sale[0], null, SalesTestKit.newKey(), "{\"staffId\":1,\"lines\":[]}").andExpect(status().isBadRequest());
        correct(sale[0], null, "not-a-uuid", ok).andExpect(status().isBadRequest());
        correct(sale[0], null, SalesTestKit.newKey(),
                "{\"staffId\":%d,\"customerId\":999999,\"lines\":[{\"productName\":\"x\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":1,\"lineAmount\":1}]}"
                        .formatted(kit.staffId("gn01-senior")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sale_return")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM sale", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("같은 판매를 서로 다른 키로 동시에 정정하면 하나만 성공한다 (ADR-030)")
    void should_allowOnlyOne_whenConcurrent() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "원래 상품", 1, 1000, null);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(5);
        var start = new java.util.concurrent.CountDownLatch(1);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return correctByStore(sale[0], null).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        var statuses = new java.util.ArrayList<Integer>();
        for (var f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409, 409);
        assertThat(count("SELECT COUNT(*) FROM sale")).isEqualTo(2);
    }
}
