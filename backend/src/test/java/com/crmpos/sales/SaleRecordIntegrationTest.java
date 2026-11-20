package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.support.TestAuth;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 판매 기록을 Controller부터 실제 MariaDB까지 확인한다 (SAL-01, ADR-006). 동시 요청을 보려고 트랜잭션 없이 실제로 커밋하고,
 * 끝나면 만든 행을 지운다. 가상 데이터다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SaleRecordIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String DIRECT_LINE = """
            {"productName":"캐시미어 머플러","itemType":"ACC","size":"FREE","color":"오트밀","qty":1,"lineAmount":217800}""";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerSummaryRepository summaryRepository;

    final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM sale_line");
        jdbc.update("UPDATE sale SET root_sale_id = NULL, origin_sale_id = NULL");
        jdbc.update("DELETE FROM sale");
        jdbc.update("DELETE FROM receipt_sequence");
        jdbc.update("DELETE FROM store_day_close");
        jdbc.update("DELETE FROM customer_member");
        jdbc.update("DELETE FROM customer_summary");
        jdbc.update("DELETE FROM customer");
        jdbc.update("DELETE FROM product_sku");
        jdbc.update("DELETE FROM product");
    }

    private long staffId(String loginId) {
        return staffRepository.findAll().stream().filter(s -> s.getLoginId().equals(loginId)).findFirst()
                .orElseThrow().getId();
    }

    private String body(long seller, Long customerId, String lines, String extra) {
        return "{\"sellerStaffId\":" + seller + (customerId == null ? "" : ",\"customerId\":" + customerId)
                + ",\"lines\":[" + lines + "]" + (extra == null ? "" : "," + extra) + "}";
    }

    private MockHttpServletRequestBuilder request(String key, String body, Long asAccount) {
        MockHttpServletRequestBuilder builder = post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        if (asAccount != null) {
            builder.header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(asAccount));
        }
        return builder;
    }

    private ResultActions send(String key, String body) throws Exception {
        return mockMvc.perform(request(key, body, null));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private long seller() {
        return staffId("gn01-senior");
    }

    // ---- 저장

    @Test
    @DisplayName("판매를 기록하면 영수증 번호·라인·합계·처음 판매와 outbox 이벤트가 한 번에 저장된다")
    void should_saveSaleWithRootAndOutbox() throws Exception {
        String key = newKey();

        String json = send(key, body(seller(), null, DIRECT_LINE, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(217800))
                .andExpect(jsonPath("$.totalQty").value(1))
                .andExpect(jsonPath("$.kind").value("NORMAL"))
                .andExpect(jsonPath("$.appliedGrade").value((Object) null))
                .andExpect(jsonPath("$.lines[0].skuId").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = mapper.readTree(json);
        long saleId = response.get("id").asLong();
        String today = LocalDate.now(KST).format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(response.get("receiptNo").asString()).isEqualTo("GN01-" + today + "-0001");
        Map<String, Object> sale = jdbc.queryForMap("SELECT * FROM sale WHERE id = ?", saleId);
        assertThat(sale.get("root_sale_id")).isEqualTo(saleId);
        assertThat(sale.get("kind")).isEqualTo("NORMAL");
        assertThat(sale.get("status")).isEqualTo("COMPLETED");
        assertThat(sale.get("idempotency_key")).isEqualTo(key);
        assertThat(sale.get("request_hash").toString()).hasSize(64);
        assertThat(sale.get("recorded_by")).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM staff WHERE login_id = 'store-gn01'", Long.class));
        assertThat(sale.get("staff_id")).isEqualTo(seller());
        assertThat(count("sale_line")).isEqualTo(1);
        Map<String, Object> outbox = jdbc.queryForMap("SELECT * FROM outbox_event");
        assertThat(outbox.get("status")).isEqualTo("PENDING");
        assertThat(outbox.get("event_type")).isEqualTo("SALE_COMPLETED");
        assertThat(outbox.get("aggregate_id")).isEqualTo(saleId);
        assertThat(mapper.readTree(outbox.get("payload").toString()).get("saleId").asLong()).isEqualTo(saleId);
    }

    @Test
    @DisplayName("같은 키로 같은 내용을 다시 보내면 200으로 같은 판매를 돌려주고 판매·이벤트는 1건이다")
    void should_replayWith200_when_sameKeyAndBody() throws Exception {
        String key = newKey();
        String body = body(seller(), null, DIRECT_LINE, null);

        String first = send(key, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = send(key, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
        assertThat(count("sale")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 다른 내용을 보내면 409 IDEMPOTENCY_KEY_REUSED이고 새로 저장되지 않는다")
    void should_return409_when_sameKeyDifferentBody() throws Exception {
        String key = newKey();
        send(key, body(seller(), null, DIRECT_LINE, null)).andExpect(status().isCreated());

        send(key, body(seller(), null, DIRECT_LINE.replace("217800", "100000"), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(count("sale")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 매장·같은 날 판매의 영수증 번호는 0001, 0002로 이어진다")
    void should_numberReceiptsInOrder() throws Exception {
        String a = mapper.readTree(send(newKey(), body(seller(), null, DIRECT_LINE, null)).andReturn()
                .getResponse().getContentAsString()).get("receiptNo").asString();
        String b = mapper.readTree(send(newKey(), body(seller(), null, DIRECT_LINE, null)).andReturn()
                .getResponse().getContentAsString()).get("receiptNo").asString();

        assertThat(a).endsWith("-0001");
        assertThat(b).endsWith("-0002");
    }

    // ---- 동시 요청

    private record Reply(int status, String body) {
    }

    private List<Reply> sendConcurrently(List<MockHttpServletRequestBuilder> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Reply>> futures = new ArrayList<>();
        for (MockHttpServletRequestBuilder req : requests) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                MvcResult result = mockMvc.perform(req).andReturn();
                return new Reply(result.getResponse().getStatus(), result.getResponse().getContentAsString());
            }));
        }
        ready.await();
        go.countDown();
        List<Reply> replies = new ArrayList<>();
        for (Future<Reply> future : futures) {
            replies.add(future.get());
        }
        pool.shutdown();
        return replies;
    }

    @Test
    @DisplayName("같은 매장에서 동시에 8건을 기록해도 영수증 번호가 겹치지 않고 0001~0008이다")
    void should_issueDistinctReceiptNumbers_whenConcurrent() throws Exception {
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(request(newKey(), body(seller(), null, DIRECT_LINE, null), null));
        }

        List<Reply> replies = sendConcurrently(requests);

        assertThat(replies).allMatch(r -> r.status() == 201);
        Set<String> numbers = new TreeSet<>();
        for (Reply reply : replies) {
            numbers.add(mapper.readTree(reply.body()).get("receiptNo").asString());
        }
        assertThat(numbers).hasSize(8);
        assertThat(numbers).allMatch(n -> n.matches("GN01-\\d{8}-000[1-8]"));
        assertThat(count("sale")).isEqualTo(8);
        assertThat(count("outbox_event")).isEqualTo(8);
    }

    @Test
    @DisplayName("같은 키로 동시에 6번 보내면 판매는 1건이고, 하나만 201이며 나머지는 같은 응답 200이다")
    void should_saveOnce_whenSameKeyConcurrent() throws Exception {
        String key = newKey();
        String body = body(seller(), null, DIRECT_LINE, null);
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(request(key, body, null));
        }

        List<Reply> replies = sendConcurrently(requests);

        assertThat(replies).filteredOn(r -> r.status() == 201).hasSize(1);
        assertThat(replies).filteredOn(r -> r.status() == 200).hasSize(5);
        Set<Long> ids = new java.util.HashSet<>();
        for (Reply reply : replies) {
            ids.add(mapper.readTree(reply.body()).get("id").asLong());
        }
        assertThat(ids).hasSize(1);
        assertThat(count("sale")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    // ---- 규칙

    @Test
    @DisplayName("다른 매장 계정이 강남점 직원을 판매 사원으로 고르면 400, 매장 계정이 아니면 403 STORE_REQUIRED")
    void should_checkSellerStoreAndRole() throws Exception {
        long hongdaeStoreAccount = staffId("store-hd01");
        long hqAccount = staffId("hq");

        mockMvc.perform(request(newKey(), body(seller(), null, DIRECT_LINE, null), hongdaeStoreAccount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sellerStaffId"));
        mockMvc.perform(request(newKey(), body(seller(), null, DIRECT_LINE, null), hqAccount))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STORE_REQUIRED"));
        send(newKey(), body(999999L, null, DIRECT_LINE, null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));

        assertThat(count("sale")).isZero();
    }

    @Test
    @DisplayName("마감된 날에는 409 DAY_CLOSED, 본사가 다시 열면 기록할 수 있다")
    void should_return409_whenDayClosed() throws Exception {
        long storeId = storeRepository.findByCode("GN01").orElseThrow().getId();
        long storeAccount = staffId("store-gn01");
        jdbc.update("INSERT INTO store_day_close (store_id, business_date, closed_by, closed_at) VALUES (?, ?, ?, NOW(6))",
                storeId, LocalDate.now(KST), storeAccount);

        send(newKey(), body(seller(), null, DIRECT_LINE, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        assertThat(count("sale")).isZero();
        assertThat(count("outbox_event")).isZero();

        jdbc.update("UPDATE store_day_close SET reopened_by = ?, reopened_at = NOW(6)", staffId("hq"));
        send(newKey(), body(seller(), null, DIRECT_LINE, null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("판매 시각: 오늘 안의 이른 시각은 받고(영수증 날짜도 그 날), 다른 날·미래는 400")
    void should_checkSoldAt() throws Exception {
        OffsetDateTime todayStart = LocalDate.now(KST).atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime yesterday = todayStart.minusMinutes(1);
        OffsetDateTime future = OffsetDateTime.now().plusHours(2);

        send(newKey(), body(seller(), null, DIRECT_LINE, "\"soldAt\":\"" + todayStart + "\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soldAt").value(org.hamcrest.Matchers.startsWith(
                        LocalDate.now(KST) + "T00:00")));
        send(newKey(), body(seller(), null, DIRECT_LINE, "\"soldAt\":\"" + yesterday + "\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("soldAt"));
        send(newKey(), body(seller(), null, DIRECT_LINE, "\"soldAt\":\"" + future + "\""))
                .andExpect(status().isBadRequest());
        assertThat(count("sale")).isEqualTo(1);
    }

    // ---- 상품·고객

    @Test
    @DisplayName("등록 상품 라인은 판매 시점의 상품 정보·정가를 복사하고, 상품이 바뀌어도 판매 기록은 그대로다")
    void should_snapshotRegisteredProduct() throws Exception {
        String product = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"SALE-1","name":"울 블렌드 롱코트","itemType":"OUTER","target":"WOMEN",
                                 "listPrice":798000,"colors":["차콜"],"sizes":["90"]}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long skuId = mapper.readTree(product).get("skus").get(0).get("skuId").asLong();
        long productId = mapper.readTree(product).get("id").asLong();

        send(newKey(), body(seller(), null, "{\"skuId\":" + skuId + ",\"qty\":1,\"lineAmount\":774060}", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].skuId").value(skuId))
                .andExpect(jsonPath("$.lines[0].productCode").value("SALE-1"))
                .andExpect(jsonPath("$.lines[0].color").value("차콜"))
                .andExpect(jsonPath("$.lines[0].size").value("90"))
                .andExpect(jsonPath("$.lines[0].listPrice").value(798000))
                .andExpect(jsonPath("$.lines[0].lineAmount").value(774060));
        jdbc.update("UPDATE product SET name = '이름이 바뀐 상품', list_price = 1 WHERE id = ?", productId);

        Map<String, Object> line = jdbc.queryForMap("SELECT * FROM sale_line");
        assertThat(line.get("product_name")).isEqualTo("울 블렌드 롱코트");
        assertThat(line.get("list_price")).isEqualTo(798000L);
        send(newKey(), body(seller(), null, "{\"skuId\":999999,\"qty\":1,\"lineAmount\":1000}", null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    private long customerWithGradeAndMember(String name, String phoneHash, String grade, String memberNickname) {
        CustomerDraft draft = CustomerDraft.builder().name(name).nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1}).phoneHash(phoneHash).phoneLast4("4821").privacyAgreed(true)
                .age14Confirmed(true).homeStoreId(1L).source(CustomerSource.STORE).build();
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(customer.getId(), 1, now));
        jdbc.update("UPDATE customer_summary SET current_grade = ? WHERE customer_id = ?", grade, customer.getId());
        if (memberNickname != null) {
            jdbc.update("INSERT INTO customer_member (customer_id, nickname, relation, created_at) VALUES (?, ?, 'SPOUSE', NOW(6))",
                    customer.getId(), memberNickname);
        }
        return customer.getId();
    }

    @Test
    @DisplayName("회원 판매는 판매 당시 등급과 착용자 호칭을 남기고, 이벤트에 고객·메모·착용자가 실린다")
    void should_storeAppliedGradeWearerAndEventForMember() throws Exception {
        long customerId = customerWithGradeAndMember("판매 김하늘", "a".repeat(64), "B", "남편");
        long memberId = jdbc.queryForObject("SELECT id FROM customer_member", Long.class);
        String lines = """
                {"productName":"캐시미어 머플러","itemType":"ACC","size":"FREE","qty":1,"lineAmount":217800,"memberId":%d}"""
                .formatted(memberId);

        send(newKey(), body(seller(), customerId, lines, "\"memo\":\"다음 시즌 스키 바지 관심\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.appliedGrade").value("B"))
                .andExpect(jsonPath("$.lines[0].wearerMemberId").value(memberId))
                .andExpect(jsonPath("$.lines[0].wearerLabel").value("남편"));

        JsonNode payload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM outbox_event", String.class));
        assertThat(payload.get("customerId").asLong()).isEqualTo(customerId);
        assertThat(payload.get("memo").asString()).isEqualTo("다음 시즌 스키 바지 관심");
        assertThat(payload.get("lines").get(0).get("memberId").asLong()).isEqualTo(memberId);
        assertThat(payload.get("staffId").asLong()).isEqualTo(seller());
    }

    @Test
    @DisplayName("없는 고객은 404, 다른 고객의 구성원을 착용자로 고르면 404, 비회원에 메모를 쓰면 400")
    void should_rejectUnknownCustomerAndForeignMember() throws Exception {
        long first = customerWithGradeAndMember("판매 고객1", "b".repeat(64), "NORMAL", "아내");
        long second = customerWithGradeAndMember("판매 고객2", "c".repeat(64), "NORMAL", null);
        long firstsMember = jdbc.queryForObject("SELECT id FROM customer_member WHERE customer_id = ?", Long.class, first);
        String foreignMemberLine = """
                {"productName":"머플러","itemType":"ACC","size":"FREE","qty":1,"lineAmount":1000,"memberId":%d}"""
                .formatted(firstsMember);

        send(newKey(), body(seller(), 999999L, DIRECT_LINE, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        send(newKey(), body(seller(), second, foreignMemberLine, null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        send(newKey(), body(seller(), null, DIRECT_LINE, "\"memo\":\"메모\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("memo"));
        assertThat(count("sale")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("요청 키가 없거나 UUID가 아니면 400")
    void should_return400_when_keyMissing() throws Exception {
        send(null, body(seller(), null, DIRECT_LINE, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
        send("abc", body(seller(), null, DIRECT_LINE, null)).andExpect(status().isBadRequest());
    }
}
