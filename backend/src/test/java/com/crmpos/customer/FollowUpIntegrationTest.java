package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 후속 연락(B4-3)을 실제 MariaDB로 확인한다: 판매 이벤트가 할 일을 만들고 닫는 규칙(BR-14), 오늘 할 일·완료·건너뛰기·
 * 방문함·단계 설정 API, 메모의 방문 예약·재연락 날짜. 테스트마다 롤백한다. 가상 데이터다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class FollowUpIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    SaleEventListener listener;
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

    final JsonMapper mapper = JsonMapper.builder().build();
    final LocalDate today = LocalDate.now(KST);
    int phoneSeq = 0;
    long nextSale = 1000;
    long nextLine = 5000;

    @BeforeEach
    void clearBaseTasks() {
        // 데모에서 남은 행이 섞이지 않게 이 테스트의 고객만 본다 (롤백되는 트랜잭션이라 지워도 안전하다)
    }

    private long id(String loginId) {
        return jdbc.queryForObject("SELECT id FROM staff WHERE login_id = ?", Long.class, loginId);
    }

    private String as(String loginId) {
        return TestAuth.bearer(id(loginId));
    }

    private long newCustomer(Long staffId, boolean marketing) {
        CustomerDraft draft = CustomerDraft.builder().name("할일 고객").nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1}).phoneHash(String.format("%064d", ++phoneSeq)).phoneLast4("4821")
                .privacyAgreed(true).age14Confirmed(true).homeStoreId(1L).source(CustomerSource.STORE)
                .staffId(staffId).marketingSms(marketing).build();
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(customer.getId(), 1, now));
        return customer.getId();
    }

    private static String iso(LocalDateTime at) {
        return at.atZone(KST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** 판매 완료 이벤트를 받는다. kind: NORMAL·EXCHANGE·CORRECTION. 반환: 판매 ID. */
    private long sale(Long customerId, long saleStaffId, LocalDateTime at, String kind, long root, Long origin)
            throws Exception {
        long saleId = nextSale++;
        long lineId = nextLine++;
        String eventId = UUID.randomUUID().toString();
        long rootId = root == 0 ? saleId : root;
        String json = """
                {"eventId":"%s","eventType":"SALE_COMPLETED","version":1,"occurredAt":"%s","saleId":%d,"receiptNo":"X",
                 "storeId":1,"customerId":%s,"staffId":%d,"recordedBy":3,"kind":"%s","originSaleId":%s,"rootSaleId":%d,
                 "exchange":null,"memo":null,"lines":[{"saleLineId":%d,"target":"WOMEN","itemType":"OUTER","qty":1,"lineAmount":100000}]}"""
                .formatted(eventId, iso(at), saleId, customerId, saleStaffId, kind, origin, rootId, lineId);
        listener.on(new OutboxMessage(eventId, "SALE_COMPLETED", rootId, json));
        em.flush();
        return saleId;
    }

    private long sale(Long customerId, long saleStaffId, LocalDateTime at) throws Exception {
        return sale(customerId, saleStaffId, at, "NORMAL", 0, null);
    }

    private void giveBack(long customerId, long saleId, long lineId, String kind, int qty) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String json = """
                {"eventId":"%s","eventType":"SALE_RETURNED","version":1,"occurredAt":"%s","saleId":%d,"returnId":1,
                 "kind":"%s","customerId":%d,"lines":[{"saleLineId":%d,"qty":%d,"amount":100000}]}"""
                .formatted(eventId, iso(LocalDateTime.now()), saleId, kind, customerId, lineId, qty);
        listener.on(new OutboxMessage(eventId, "SALE_RETURNED", saleId, json));
        em.flush();
    }

    private List<Map<String, Object>> tasks(long customerId) {
        return jdbc.queryForList("SELECT * FROM follow_up_task WHERE customer_id = ? ORDER BY id", customerId);
    }

    private List<Map<String, Object>> open(long customerId) {
        return jdbc.queryForList("SELECT * FROM follow_up_task WHERE customer_id = ? AND status = 'OPEN' ORDER BY due_date, id",
                customerId);
    }

    private static LocalDate date(Object value) {
        return ((java.sql.Date) value).toLocalDate();
    }

    // ===== 이벤트 처리: 할 일 만들기·닫기 (BR-14) =====

    @Test
    @DisplayName("회원 판매를 받으면 2일·2주·2달 뒤 할 일 3개가 생기고 단계 이름·홍보 여부가 복사된다")
    void should_createThreeTasks_whenMemberSale() throws Exception {
        long c = newCustomer(null, true);
        LocalDateTime at = today.atTime(10, 0);

        long saleId = sale(c, id("gn01-senior"), at);

        List<Map<String, Object>> rows = tasks(c);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("rule_name")).containsExactly("감사 인사", "착용감 확인", "신상·재방문 안내");
        assertThat(rows).extracting(r -> date(r.get("due_date"))).containsExactly(today.plusDays(2), today.plusWeeks(2),
                today.plusMonths(2));
        assertThat(rows).extracting(r -> r.get("promotional")).containsExactly(false, false, true);
        assertThat(rows).extracting(r -> r.get("kind")).containsOnly("SALE");
        assertThat(rows).extracting(r -> r.get("sale_id")).containsOnly(saleId);
        assertThat(rows).extracting(r -> r.get("status")).containsOnly("OPEN");
    }

    @Test
    @DisplayName("받는 사람: 담당 직원이 활성이면 담당, 담당이 비활성이거나 없으면 판매 사원, 매장 계정이 맡은 고객은 매장 계정")
    void should_chooseAssignee() throws Exception {
        long senior = id("gn01-senior");
        long junior = id("gn01-junior");

        long withStaff = newCustomer(junior, true);
        sale(withStaff, senior, today.atTime(9, 0));
        assertThat(tasks(withStaff)).extracting(r -> r.get("staff_id")).containsOnly(junior);

        long noStaff = newCustomer(null, true);
        sale(noStaff, senior, today.atTime(9, 0));
        assertThat(tasks(noStaff)).extracting(r -> r.get("staff_id")).containsOnly(senior);

        long store = id("store-gn01");
        long storeOwned = newCustomer(store, true);
        sale(storeOwned, senior, today.atTime(9, 0));
        assertThat(tasks(storeOwned)).extracting(r -> r.get("staff_id")).containsOnly(store);

        jdbc.update("UPDATE staff SET active = 0 WHERE id = ?", junior);
        em.clear(); // JDBC로 바꾼 값을 JPA가 다시 읽게 한다
        long inactive = newCustomer(junior, true);
        sale(inactive, senior, today.atTime(9, 0));
        assertThat(tasks(inactive)).extracting(r -> r.get("staff_id")).containsOnly(senior);
    }

    @Test
    @DisplayName("비회원 판매와 교환은 할 일을 만들지 않는다")
    void should_notCreate_forExchange() throws Exception {
        long c = newCustomer(null, true);
        long first = sale(c, id("gn01-senior"), today.atTime(9, 0));
        jdbc.update("DELETE FROM follow_up_task");

        sale(c, id("gn01-senior"), today.atTime(11, 0), "EXCHANGE", first, first);

        assertThat(tasks(c)).isEmpty();
    }

    @Test
    @DisplayName("꺼 둔 단계는 만들지 않고, 설정을 바꿔도 이미 만든 할 일은 그대로다")
    void should_skipDisabledRule_andKeepExistingTasks() throws Exception {
        long c1 = newCustomer(null, true);
        sale(c1, id("gn01-senior"), today.atTime(9, 0));
        jdbc.update("UPDATE follow_up_rule SET active = 0, name = '바뀐 이름' WHERE sort_order = 1");

        long c2 = newCustomer(null, true);
        sale(c2, id("gn01-senior"), today.atTime(9, 0));

        assertThat(tasks(c1)).extracting(r -> r.get("rule_name")).contains("감사 인사"); // 복사본이라 그대로
        assertThat(tasks(c2)).hasSize(2);
    }

    @Test
    @DisplayName("같은 고객이 또 사면 이전 열린 할 일은 REPLACED, 새 구매 기준으로 다시 만든다")
    void should_replacePreviousTasks_whenNewerPurchase() throws Exception {
        long c = newCustomer(null, true);
        long first = sale(c, id("gn01-senior"), today.minusDays(3).atTime(10, 0));
        long second = sale(c, id("gn01-senior"), today.atTime(10, 0));

        assertThat(tasks(c).stream().filter(r -> r.get("sale_id").equals(first)))
                .extracting(r -> r.get("status")).containsOnly("REPLACED");
        assertThat(open(c)).hasSize(3).extracting(r -> r.get("sale_id")).containsOnly(second);
    }

    @Test
    @DisplayName("지난 날짜 판매(더 최근 구매가 있음)는 할 일을 만들지도, 더 최근 구매의 할 일을 바꾸지도 않는다")
    void should_notTouchTasks_whenOlderPurchaseArrives() throws Exception {
        long c = newCustomer(null, true);
        long recent = sale(c, id("gn01-senior"), today.atTime(10, 0));

        sale(c, id("gn01-senior"), today.minusDays(40).atTime(10, 0));

        assertThat(open(c)).hasSize(3).extracting(r -> r.get("sale_id")).containsOnly(recent);
        assertThat(tasks(c)).hasSize(3);
    }

    @Test
    @DisplayName("정정: 원래 판매 시각으로 다시 만들되 만기일이 이미 지난 단계는 만들지 않는다")
    void should_skipPastStages_whenCorrection() throws Exception {
        long c = newCustomer(null, true);
        LocalDateTime tenDaysAgo = today.minusDays(10).atTime(10, 0);

        sale(c, id("gn01-senior"), tenDaysAgo, "CORRECTION", 900, 900L);

        // 2일 뒤(8일 전)는 지났으니 빼고, 2주 뒤(4일 뒤)와 2달 뒤는 만든다
        assertThat(tasks(c)).extracting(r -> date(r.get("due_date"))).containsExactly(tenDaysAgo.toLocalDate().plusWeeks(2),
                tenDaysAgo.toLocalDate().plusMonths(2));
    }

    @Test
    @DisplayName("구매 건의 상품이 모두 반품·기록 취소되면 남은 할 일은 CANCELLED, 일부만 반품이면 그대로다")
    void should_cancelTasks_whenEverythingGivenBack() throws Exception {
        long c = newCustomer(null, true);
        long saleId = sale(c, id("gn01-senior"), today.atTime(10, 0));
        long lineId = nextLine - 1;

        // 상품이 남아 있으면 유지: 2개를 산 판매의 1개만 반품
        jdbc.update("UPDATE customer_purchase SET qty = 2 WHERE sale_line_id = ?", lineId);
        em.clear();
        giveBack(c, saleId, lineId, "RETURN", 1);
        assertThat(open(c)).hasSize(3);

        giveBack(c, saleId, lineId, "VOID", 1);
        assertThat(tasks(c)).extracting(r -> r.get("status")).containsOnly("CANCELLED");
    }

    @Test
    @DisplayName("메모로 만든 방문 예약·재연락 할 일은 새 구매로 닫히지 않는다")
    void should_keepManualTasks_whenNewPurchase() throws Exception {
        long c = newCustomer(null, true);
        long junior = id("gn01-junior");
        jdbc.update("INSERT INTO follow_up_task (customer_id, staff_id, kind, rule_name, promotional, due_date, status, created_at) "
                + "VALUES (?, ?, 'VISIT', '방문 예정', 0, ?, 'OPEN', NOW(6))", c, junior, today.plusDays(5));

        sale(c, id("gn01-senior"), today.atTime(10, 0));

        assertThat(jdbc.queryForObject("SELECT status FROM follow_up_task WHERE kind = 'VISIT' AND customer_id = ?",
                String.class, c)).isEqualTo("OPEN");
    }

    // ===== API =====

    private ResultActions api(String path, String account) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, as(account)));
    }

    private ResultActions postJson(String path, String account, String body) throws Exception {
        return mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, as(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long insertTask(long customerId, long staffId, String kind, LocalDate due, String stage, boolean promotional) {
        jdbc.update("INSERT INTO follow_up_task (customer_id, staff_id, kind, rule_name, promotional, due_date, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'OPEN', NOW(6))", customerId, staffId, kind, stage, promotional, due);
        return jdbc.queryForObject("SELECT MAX(id) FROM follow_up_task", Long.class);
    }

    @Test
    @DisplayName("오늘 할 일: 만기일이 오늘이거나 지난 열린 것만, 오래된 순, 밀린 날 수와 고객 정보(뒷자리만) 포함")
    void should_listToday_oldestFirst() throws Exception {
        long c = newCustomer(null, true);
        long junior = id("gn01-junior");
        long overdue = insertTask(c, junior, "SALE", today.minusDays(3), "착용감 확인", false);
        long dueToday = insertTask(c, junior, "SALE", today, "감사 인사", false);
        insertTask(c, junior, "SALE", today.plusDays(1), "내일", false); // 아직 아님
        jdbc.update("UPDATE follow_up_task SET status = 'DONE' WHERE id = ?",
                insertTask(c, junior, "SALE", today.minusDays(9), "이미 끝남", false));

        api("/api/v1/follow-ups/today", "gn01-junior").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(overdue)).andExpect(jsonPath("$.content[0].overdueDays").value(3))
                .andExpect(jsonPath("$.content[1].id").value(dueToday)).andExpect(jsonPath("$.content[1].overdueDays").value(0))
                .andExpect(jsonPath("$.content[0].customerName").value("할일 고객"))
                .andExpect(jsonPath("$.content[0].phoneMasked").value("****-4821"))
                .andExpect(jsonPath("$.content[0].grade").value("NORMAL")).andExpect(jsonPath("$.content[0].assignee").value("STAFF"))
                .andExpect(jsonPath("$.content[0].staffName").value("강남 주니어"))
                .andExpect(jsonPath("$.content[0].marketingAllowed").value(true));
    }

    @Test
    @DisplayName("오늘 할 일의 범위: 직원은 내 것만, 매장 계정은 매장 전체(매장 계정이 맡은 것은 STORE), 본사는 storeId 필수")
    void should_scopeTodayByRole() throws Exception {
        long c = newCustomer(null, false);
        insertTask(c, id("gn01-junior"), "SALE", today, "주니어", false);
        insertTask(c, id("gn01-senior"), "SALE", today, "시니어", false);
        insertTask(c, id("store-gn01"), "SALE", today, "매장", true);
        insertTask(c, id("hd01-senior"), "SALE", today, "홍대", false);
        long gangnam = jdbc.queryForObject("SELECT id FROM store WHERE code = 'GN01'", Long.class);

        api("/api/v1/follow-ups/today", "gn01-junior").andExpect(jsonPath("$.totalElements").value(1));
        api("/api/v1/follow-ups/today", "store-gn01").andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.stage=='매장')].assignee").value("STORE"))
                .andExpect(jsonPath("$.content[?(@.stage=='매장')].promotional").value(true))
                .andExpect(jsonPath("$.content[?(@.stage=='매장')].marketingAllowed").value(false));
        api("/api/v1/follow-ups/today?storeId=" + gangnam, "hq").andExpect(jsonPath("$.totalElements").value(3));
        api("/api/v1/follow-ups/today", "hq").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("storeId"));
        api("/api/v1/follow-ups/today?storeId=999999", "hq").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    @DisplayName("고객의 할 일: 열린 것 먼저, 판매 할 일은 기준 판매 시각(soldAt), 메모 할 일은 메모 내용(noteContent)")
    void should_listCustomerTasks_openFirst() throws Exception {
        long c = newCustomer(null, true);
        long saleId = sale(c, id("gn01-senior"), today.atTime(10, 0));
        jdbc.update("UPDATE follow_up_task SET status = 'DONE', closed_at = NOW(6) WHERE rule_name = '감사 인사'");
        em.clear(); // JDBC로 바꾼 값을 JPA가 다시 읽게 한다

        api("/api/v1/customers/" + c + "/follow-ups", "gn01-junior").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].status").value("OPEN")).andExpect(jsonPath("$.content[2].status").value("DONE"))
                .andExpect(jsonPath("$.content[0].kind").value("SALE")).andExpect(jsonPath("$.content[0].saleId").value(saleId))
                .andExpect(jsonPath("$.content[0].soldAt").exists()).andExpect(jsonPath("$.content[0].noteContent").doesNotExist());
        api("/api/v1/customers/999999/follow-ups", "gn01-junior").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("연락 결과로 완료: 연락 메모(CALL)가 생기고 할 일이 DONE이 되며, 같은 할 일을 또 보내면 409")
    void should_complete_withContactNote() throws Exception {
        long c = newCustomer(null, true);
        long task = insertTask(c, id("gn01-junior"), "SALE", today, "감사 인사", false);

        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-junior",
                "{\"callResult\":\"COMPLETED\",\"channel\":\"KAKAO\",\"content\":\"감사 인사 전함\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.closedAt").exists());

        em.flush();
        var note = jdbc.queryForMap("SELECT * FROM consult_note WHERE customer_id = ?", c);
        assertThat(note.get("type")).isEqualTo("CALL");
        assertThat(note.get("staff_id")).isEqualTo(id("gn01-junior"));
        assertThat(jdbc.queryForObject("SELECT note_id FROM follow_up_task WHERE id = ?", Long.class, task))
                .isEqualTo(note.get("id"));
        api("/api/v1/customers/" + c + "/notes", "gn01-junior").andExpect(jsonPath("$.content[0].followUpId").value(task));

        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-junior",
                "{\"callResult\":\"ABSENT\",\"channel\":\"CALL\",\"content\":\"또\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_ALREADY_CLOSED"));
    }

    @Test
    @DisplayName("완료 권한·검사: 남의 할 일 403, 다른 매장 매장 계정 403, 없으면 404, 매장 계정은 직원 필수, 결과 없으면 400 (할 일은 안 닫힌다)")
    void should_checkCompletePermissionsAndInput() throws Exception {
        long c = newCustomer(null, true);
        long task = insertTask(c, id("gn01-junior"), "SALE", today, "감사 인사", false);
        String body = "{\"callResult\":\"COMPLETED\",\"channel\":\"CALL\",\"content\":\"완료\"}";

        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-senior", body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        postJson("/api/v1/follow-ups/" + task + "/complete", "store-hd01", body).andExpect(status().isForbidden());
        postJson("/api/v1/follow-ups/" + task + "/complete", "hq", body).andExpect(status().isForbidden());
        postJson("/api/v1/follow-ups/999999/complete", "gn01-junior", body).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_NOT_FOUND"));
        postJson("/api/v1/follow-ups/" + task + "/complete", "store-gn01", body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-junior", "{\"content\":\"결과 없음\"}")
                .andExpect(status().isBadRequest());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM follow_up_task WHERE id = ?", String.class, task)).isEqualTo("OPEN");

        // 매장 계정은 직원을 골라 완료한다 (연락한 직원이 작성 직원)
        postJson("/api/v1/follow-ups/" + task + "/complete", "store-gn01",
                "{\"staffId\":%d,\"callResult\":\"RECALL\",\"channel\":\"SMS\",\"content\":\"재연락 필요\"}".formatted(id("gn01-senior")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT staff_id FROM consult_note WHERE customer_id = ?", Long.class, c))
                .isEqualTo(id("gn01-senior"));
    }

    @Test
    @DisplayName("건너뛰기는 SKIPPED로 닫고 직원은 내 할 일만, 닫힌 것을 또 건너뛰면 409")
    void should_skip() throws Exception {
        long c = newCustomer(null, true);
        long task = insertTask(c, id("gn01-junior"), "SALE", today, "감사 인사", false);

        postJson("/api/v1/follow-ups/" + task + "/skip", "gn01-senior", "{}").andExpect(status().isForbidden());
        postJson("/api/v1/follow-ups/" + task + "/skip", "gn01-junior", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
        postJson("/api/v1/follow-ups/" + task + "/skip", "store-gn01", "{}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("방문함: 방문 예약(VISIT)만 완료되고 연락 기록은 안 남는다, 판매 할 일은 400")
    void should_markVisited() throws Exception {
        long c = newCustomer(null, true);
        long visit = insertTask(c, id("gn01-junior"), "VISIT", today, "방문 예정", false);
        long sale = insertTask(c, id("gn01-junior"), "SALE", today, "감사 인사", false);

        postJson("/api/v1/follow-ups/" + sale + "/visited", "gn01-junior", "{}").andExpect(status().isBadRequest());
        postJson("/api/v1/follow-ups/" + visit + "/visited", "gn01-junior", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.kind").value("VISIT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consult_note WHERE customer_id = ?", Integer.class, c)).isZero();
    }

    @Test
    @DisplayName("완료할 때 재연락(RECALL)이면 reminder로 다시 연락할 날을 정하고, 그날 RECALL 할 일이 생긴다 (CAL-11)")
    void should_createRecallTask_whenCompleteWithRecallReminder() throws Exception {
        long c = newCustomer(null, true);
        long task = insertTask(c, id("gn01-junior"), "SALE", today, "감사 인사", false);
        LocalDate again = today.plusDays(4);

        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-junior",
                "{\"callResult\":\"RECALL\",\"channel\":\"CALL\",\"content\":\"바쁘대서 나중에\","
                        + "\"reminder\":{\"date\":\"%s\",\"staffId\":%d}}".formatted(again, id("gn01-senior")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        em.flush();

        var recall = jdbc.queryForMap("SELECT * FROM follow_up_task WHERE customer_id = ? AND kind = 'RECALL'", c);
        assertThat(date(recall.get("due_date"))).isEqualTo(again);
        assertThat(recall.get("staff_id")).isEqualTo(id("gn01-senior"));
        assertThat(recall.get("status")).isEqualTo("OPEN");
        assertThat(recall.get("source_note_id")).isEqualTo(jdbc.queryForObject(
                "SELECT note_id FROM follow_up_task WHERE id = ?", Long.class, task)); // 완료 연락 메모에서 만든 할 일
        api("/api/v1/customers/" + c + "/notes", "gn01-junior").andExpect(jsonPath("$.content[0].reminderDate").value(again.toString()));
    }

    @Test
    @DisplayName("완료할 때 재연락이 아닌 결과에 reminder를 보내거나 과거 날짜면 400이고 할 일은 안 닫힌다 (메모와 같은 문구)")
    void should_rejectInvalidReminder_whenComplete() throws Exception {
        long c = newCustomer(null, true);
        long task = insertTask(c, id("gn01-junior"), "SALE", today, "감사 인사", false);

        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-junior",
                "{\"callResult\":\"COMPLETED\",\"channel\":\"CALL\",\"content\":\"통화\",\"reminder\":{\"date\":\"%s\"}}"
                        .formatted(today.plusDays(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reminderDate"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("방문 예약이나 재연락 메모에만 날짜를 정할 수 있습니다."));
        postJson("/api/v1/follow-ups/" + task + "/complete", "gn01-junior",
                "{\"callResult\":\"RECALL\",\"channel\":\"CALL\",\"content\":\"과거\",\"reminder\":{\"date\":\"%s\"}}"
                        .formatted(today.minusDays(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("오늘 이후 날짜를 골라 주세요."));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM follow_up_task WHERE id = ?", String.class, task)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_up_task WHERE kind = 'RECALL'", Integer.class)).isZero();
    }

    // ===== 메모의 방문 예약·재연락 날짜 (CAL-10·11) =====

    private ResultActions note(String account, long customerId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/customers/" + customerId + "/notes")
                .header(HttpHeaders.AUTHORIZATION, as(account)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("방문 예약 메모는 날짜가 필수이고, 그 날짜로 VISIT 할 일이 생겨 받는 직원(기본 작성 직원)에게 간다")
    void should_createVisitTask_fromReservationNote() throws Exception {
        long c = newCustomer(null, true);
        LocalDate visitDay = today.plusDays(7);

        note("gn01-junior", c, "{\"type\":\"RESERVATION\",\"content\":\"다음 주 방문\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reminderDate"));
        note("gn01-junior", c, "{\"type\":\"RESERVATION\",\"content\":\"다음 주 방문\",\"reminder\":{\"date\":\"%s\"}}"
                .formatted(visitDay)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderDate").value(visitDay.toString()));

        var row = tasks(c).get(0);
        assertThat(row.get("kind")).isEqualTo("VISIT");
        assertThat(row.get("staff_id")).isEqualTo(id("gn01-junior"));
        assertThat(date(row.get("due_date"))).isEqualTo(visitDay);
        assertThat(row.get("source_note_id")).isNotNull();
        assertThat(row.get("sale_id")).isNull();
        assertThat(row.get("rule_name")).isEqualTo("방문 예정");
        api("/api/v1/customers/" + c + "/follow-ups", "gn01-junior").andExpect(jsonPath("$.content[0].noteContent").value("다음 주 방문"))
                .andExpect(jsonPath("$.content[0].saleId").doesNotExist());
        api("/api/v1/customers/" + c + "/notes", "gn01-junior").andExpect(jsonPath("$.content[0].reminderDate").value(visitDay.toString()));
    }

    @Test
    @DisplayName("재연락(CALL+RECALL)에 날짜를 정하면 RECALL 할 일이 생기고, 받는 직원은 고를 수 있다 (같은 매장 직원만)")
    void should_createRecallTask_withChosenReceiver() throws Exception {
        long c = newCustomer(null, true);
        LocalDate day = today.plusDays(3);

        note("gn01-junior", c, "{\"type\":\"CALL\",\"callResult\":\"RECALL\",\"channel\":\"CALL\",\"content\":\"바빠서 다음에\","
                + "\"reminder\":{\"date\":\"%s\",\"staffId\":%d}}".formatted(day, id("gn01-senior"))).andExpect(status().isCreated());

        var row = tasks(c).get(0);
        assertThat(row.get("kind")).isEqualTo("RECALL");
        assertThat(row.get("staff_id")).isEqualTo(id("gn01-senior"));
        assertThat(row.get("rule_name")).isEqualTo("다시 연락");

        note("gn01-junior", c, "{\"type\":\"CALL\",\"callResult\":\"RECALL\",\"channel\":\"CALL\",\"content\":\"x\","
                + "\"reminder\":{\"date\":\"%s\",\"staffId\":%d}}".formatted(day, id("hd01-senior"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reminder.staffId"));
    }

    @Test
    @DisplayName("날짜는 오늘 이후만이고, 예약·재연락이 아닌 메모에는 정할 수 없다 (조용히 버리지 않고 400)")
    void should_rejectInvalidReminders() throws Exception {
        long c = newCustomer(null, true);

        note("gn01-junior", c, "{\"type\":\"RESERVATION\",\"content\":\"과거\",\"reminder\":{\"date\":\"%s\"}}"
                .formatted(today.minusDays(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reminderDate"));
        note("gn01-junior", c, "{\"type\":\"CONSULT\",\"content\":\"상담\",\"reminder\":{\"date\":\"%s\"}}".formatted(today.plusDays(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("reminderDate"));
        note("gn01-junior", c, "{\"type\":\"CALL\",\"callResult\":\"COMPLETED\",\"channel\":\"CALL\",\"content\":\"통화\","
                + "\"reminder\":{\"date\":\"%s\"}}".formatted(today.plusDays(1))).andExpect(status().isBadRequest());
        note("gn01-junior", c, "{\"type\":\"RESERVATION\",\"content\":\"오늘 방문\",\"reminder\":{\"date\":\"%s\"}}".formatted(today))
                .andExpect(status().isCreated()); // 오늘은 가능 (경계)
        assertThat(tasks(c)).hasSize(1);
    }

    @Test
    @DisplayName("방문 예약·재연락 메모를 지우면 아직 열린 그 할 일은 CANCELLED, 이미 끝난 것은 그대로다")
    void should_cancelOpenTask_whenSourceNoteDeleted() throws Exception {
        long c = newCustomer(null, true);
        long junior = id("gn01-junior");
        String reminder = "\"reminder\":{\"date\":\"%s\"}".formatted(today.plusDays(2));
        long openNote = noteId(note("gn01-junior", c, "{\"type\":\"RESERVATION\",\"content\":\"열린 예약\"," + reminder + "}"));
        long doneNote = noteId(note("gn01-junior", c, "{\"type\":\"RESERVATION\",\"content\":\"끝난 예약\"," + reminder + "}"));
        em.flush();
        jdbc.update("UPDATE follow_up_task SET status = 'DONE', closed_at = NOW(6) WHERE source_note_id = ?", doneNote);
        em.clear();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/notes/" + openNote)
                .header(HttpHeaders.AUTHORIZATION, as("gn01-junior"))).andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/notes/" + doneNote)
                .header(HttpHeaders.AUTHORIZATION, as("gn01-junior"))).andExpect(status().isNoContent());
        em.flush();

        assertThat(jdbc.queryForObject("SELECT status FROM follow_up_task WHERE source_note_id = ?", String.class, openNote))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM follow_up_task WHERE source_note_id = ?", String.class, doneNote))
                .isEqualTo("DONE");
        assertThat(junior).isPositive();
    }

    private long noteId(ResultActions created) throws Exception {
        return mapper.readTree(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    // ===== 단계 설정 =====

    @Test
    @DisplayName("단계 설정 조회는 누구나, 저장은 본사만. id가 있으면 고치고 없으면 새 단계, 목록에 없는 단계는 지우지 않고 끈다")
    void should_saveRules_disableMissing() throws Exception {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT id FROM follow_up_rule ORDER BY sort_order");
        long first = ((Number) before.get(0).get("id")).longValue();
        long second = ((Number) before.get(1).get("id")).longValue();

        api("/api/v1/follow-up-rules", "gn01-junior").andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("감사 인사"))
                .andExpect(jsonPath("$[0].amount").value(2)).andExpect(jsonPath("$[0].unit").value("DAY"))
                .andExpect(jsonPath("$[2].promotional").value(true)).andExpect(jsonPath("$[0].enabled").value(true));

        String body = "[{\"id\":%d,\"name\":\"감사 문자\",\"amount\":3,\"unit\":\"DAY\",\"promotional\":false,\"enabled\":true},"
                .formatted(first) + "{\"name\":\"새 단계\",\"amount\":1,\"unit\":\"MONTH\",\"promotional\":true,\"enabled\":true}]";
        mockMvc.perform(put("/api/v1/follow-up-rules").header(HttpHeaders.AUTHORIZATION, as("store-gn01"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/follow-up-rules").header(HttpHeaders.AUTHORIZATION, as("hq"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='감사 문자')].amount").value(3))
                .andExpect(jsonPath("$[?(@.name=='새 단계')].enabled").value(true));

        em.flush();
        assertThat(jdbc.queryForObject("SELECT active FROM follow_up_rule WHERE id = ?", Boolean.class, second)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM follow_up_rule", Integer.class)).isEqualTo(4); // 지우지 않았다
    }

    @Test
    @DisplayName("단계 설정 입력 오류는 한 번에 400: 이름·기간·단위·없는 id")
    void should_validateRules() throws Exception {
        mockMvc.perform(put("/api/v1/follow-up-rules").header(HttpHeaders.AUTHORIZATION, as("hq"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"name\":\"\",\"amount\":0,\"unit\":\"DAY\",\"promotional\":false,\"enabled\":true},"
                        + "{\"id\":999999,\"name\":\"가\",\"amount\":1,\"unit\":\"DAY\",\"promotional\":false,\"enabled\":true}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.length()").value(3));
        mockMvc.perform(put("/api/v1/follow-up-rules").header(HttpHeaders.AUTHORIZATION, as("hq"))
                .contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isBadRequest());
    }
}
