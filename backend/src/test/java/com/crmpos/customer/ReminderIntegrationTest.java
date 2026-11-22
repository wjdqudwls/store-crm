package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.customer.service.ReminderService;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.RecordingPushSender;
import com.crmpos.support.TestAuth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 9시 방문 예약·재연락 알림, 자정 만기 알림, 담당 고객의 새 메모 알림을 확인한다 (CAL-09·10·11·12, BR-14).
 * 스케줄은 끄고 서비스를 직접 부른다. 롤백한다. 가상 데이터다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(ReminderIntegrationTest.PushForTest.class)
class ReminderIntegrationTest {

    @TestConfiguration
    static class PushForTest {
        @Bean
        @Primary
        RecordingPushSender reminderPushSender() {
            return new RecordingPushSender();
        }
    }

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired
    ReminderService reminderService;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerSummaryRepository summaryRepository;
    @Autowired
    RecordingPushSender push;
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager em;

    int seq = 0;

    @BeforeEach
    void reset() {
        push.reset();
    }

    private long id(String loginId) {
        return jdbc.queryForObject("SELECT id FROM staff WHERE login_id = ?", Long.class, loginId);
    }

    private long customer(String name, Long staffId) {
        CustomerDraft draft = CustomerDraft.builder().name(name).nationality(Nationality.DOMESTIC).phoneEnc(new byte[] {1})
                .phoneHash(String.format("%064d", ++seq)).phoneLast4("4821").privacyAgreed(true).age14Confirmed(true)
                .homeStoreId(1L).staffId(staffId).source(CustomerSource.STORE).build();
        LocalDateTime now = LocalDateTime.now();
        Customer c = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(c.getId(), 1, now));
        return c.getId();
    }

    private long task(long customerId, long staffId, String kind, LocalDate due) {
        jdbc.update("INSERT INTO follow_up_task (customer_id, staff_id, kind, rule_name, promotional, due_date, status, created_at) "
                + "VALUES (?, ?, ?, ?, 0, ?, 'OPEN', NOW(6))", customerId, staffId, kind, "VISIT".equals(kind) ? "방문 예정" : "다시 연락", due);
        return jdbc.queryForObject("SELECT MAX(id) FROM follow_up_task", Long.class);
    }

    private void timing(String loginId, String timing) {
        jdbc.update("UPDATE staff SET notify_timing = ? WHERE login_id = ?", timing, loginId);
        em.clear();
    }

    private List<String> messages(String loginId) {
        em.flush();
        return jdbc.queryForList("SELECT message FROM staff_notification WHERE staff_id = ? ORDER BY id", String.class, id(loginId));
    }

    // --- 9시 알림 ---

    @Test
    @DisplayName("전날(기본) 설정이면 내일 방문 예약 1명은 이름으로 알리고, 푸시도 같은 문구로 간다")
    void should_notifyDayBefore_singleVisit() {
        long junior = id("gn01-junior");
        push.reset();
        jdbc.update("INSERT INTO push_subscription (staff_id, endpoint, p256dh, auth, created_at) VALUES (?, 'https://p/1', 'k', 'a', NOW(6))", junior);
        task(customer("김하늘", junior), junior, "VISIT", TODAY.plusDays(1));
        task(customer("오늘손님", junior), junior, "VISIT", TODAY); // 전날 설정이라 오늘 만기는 안 알린다

        int sent = reminderService.run(TODAY);

        assertThat(sent).isEqualTo(1);
        assertThat(messages("gn01-junior")).containsExactly("내일 김하늘님 방문 예정이에요.");
        assertThat(push.messages).containsExactly("내일 김하늘님 방문 예정이에요.");
    }

    @Test
    @DisplayName("같은 날 같은 종류가 여럿이면 하나로 묶고, 방문과 재연락은 따로 알린다")
    void should_groupBySameKind() {
        long junior = id("gn01-junior");
        task(customer("김하늘", junior), junior, "VISIT", TODAY.plusDays(1));
        task(customer("박서연", junior), junior, "VISIT", TODAY.plusDays(1));
        task(customer("윤가람", junior), junior, "VISIT", TODAY.plusDays(1));
        task(customer("한예린", junior), junior, "RECALL", TODAY.plusDays(1));

        reminderService.run(TODAY);

        assertThat(messages("gn01-junior")).containsExactlyInAnyOrder("내일 고객님들이 방문할 예정이에요. (3명)",
                "내일 한예린님께 다시 연락할 날이에요.");
    }

    @Test
    @DisplayName("당일 아침(SAME_DAY)은 오늘 만기만, 둘 다(BOTH)는 내일과 오늘 모두, 안 받음(NONE)은 아무것도 안 받는다")
    void should_followTimingSettings() {
        long senior = id("gn01-senior");
        long junior = id("gn01-junior");
        long staff = id("gn01-staff");
        timing("gn01-senior", "SAME_DAY");
        timing("gn01-junior", "BOTH");
        timing("gn01-staff", "NONE");
        for (long s : new long[] {senior, junior, staff}) {
            task(customer("내일손님" + s, s), s, "VISIT", TODAY.plusDays(1));
            task(customer("오늘손님" + s, s), s, "RECALL", TODAY);
        }

        reminderService.run(TODAY);

        assertThat(messages("gn01-senior")).containsExactly("오늘 오늘손님" + senior + "님께 다시 연락할 날이에요.");
        assertThat(messages("gn01-junior")).containsExactlyInAnyOrder("내일 내일손님" + junior + "님 방문 예정이에요.",
                "오늘 오늘손님" + junior + "님께 다시 연락할 날이에요.");
        assertThat(messages("gn01-staff")).isEmpty();
    }

    @Test
    @DisplayName("끝난 할 일·판매 할 일·매장 계정이 받는 할 일·비활성 직원은 알리지 않고, 다시 돌려도 겹치지 않는다")
    void should_skipClosedOtherKindsAndInactive_andBeIdempotent() {
        long junior = id("gn01-junior");
        long done = task(customer("끝남", junior), junior, "VISIT", TODAY.plusDays(1));
        jdbc.update("UPDATE follow_up_task SET status = 'DONE' WHERE id = ?", done);
        task(customer("판매할일", junior), junior, "SALE", TODAY.plusDays(1));
        task(customer("매장이받음", id("store-gn01")), id("store-gn01"), "VISIT", TODAY.plusDays(1));
        long gone = id("gn01-staff");
        task(customer("퇴사직원", gone), gone, "VISIT", TODAY.plusDays(1));
        jdbc.update("UPDATE staff SET active = 0 WHERE id = ?", gone);
        task(customer("김하늘", junior), junior, "VISIT", TODAY.plusDays(1));
        em.clear();

        assertThat(reminderService.run(TODAY)).isEqualTo(1);
        assertThat(reminderService.run(TODAY)).isZero(); // 같은 날 다시 돌려도 안 겹친다

        assertThat(messages("gn01-junior")).containsExactly("내일 김하늘님 방문 예정이에요.");
        assertThat(messages("gn01-staff")).isEmpty();
        assertThat(messages("gn01-senior")).isEmpty();
    }

    // --- 자정 만기 알림 ---

    @Test
    @DisplayName("오늘 만기가 된 판매 후속 연락은 받는 직원에게 '고객 · 구매 2주 · 단계'로 알린다 (매장 계정·미래 만기는 제외)")
    void should_notifyDueSaleTasks() {
        long junior = id("gn01-junior");
        long rule = jdbc.queryForObject("SELECT id FROM follow_up_rule WHERE name = '착용감 확인'", Long.class);
        long c = customer("김하늘", junior);
        jdbc.update("INSERT INTO follow_up_task (customer_id, staff_id, kind, sale_id, rule_id, rule_name, promotional, due_date, status, created_at) "
                + "VALUES (?, ?, 'SALE', 88231, ?, '착용감 확인', 0, ?, 'OPEN', NOW(6))", c, junior, rule, TODAY);
        jdbc.update("INSERT INTO follow_up_task (customer_id, staff_id, kind, sale_id, rule_id, rule_name, promotional, due_date, status, created_at) "
                + "VALUES (?, ?, 'SALE', 88231, ?, '착용감 확인', 0, ?, 'OPEN', NOW(6))", c, junior, rule, TODAY.plusDays(3));
        jdbc.update("INSERT INTO follow_up_task (customer_id, staff_id, kind, sale_id, rule_id, rule_name, promotional, due_date, status, created_at) "
                + "VALUES (?, ?, 'SALE', 88231, ?, '착용감 확인', 0, ?, 'OPEN', NOW(6))", c, id("store-gn01"), rule, TODAY);
        em.clear();

        assertThat(reminderService.notifyDueSaleTasks(TODAY)).isEqualTo(1);
        assertThat(reminderService.notifyDueSaleTasks(TODAY)).isZero();

        assertThat(messages("gn01-junior")).containsExactly("김하늘 · 구매 2주 · 착용감 확인");
        assertThat(jdbc.queryForObject("SELECT type FROM staff_notification WHERE staff_id = ?", String.class, junior)).isEqualTo("FOLLOW_UP");
    }

    // --- 담당 고객의 새 메모 ---

    private void write(String account, long customerId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/customers/" + customerId + "/notes").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(id(account)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("담당이 아닌 사람이 메모를 남기면 담당 직원 알림함에 알림이 가고, 문구에 메모 내용은 없다")
    void should_notifyOwner_whenOthersWriteNote() throws Exception {
        long owner = id("gn01-senior");
        long c = customer("김하늘", owner);

        write("gn01-junior", c, "{\"type\":\"CONSULT\",\"content\":\"비밀 상담 내용\"}");

        assertThat(messages("gn01-senior")).containsExactly("강남 주니어님이 김하늘님 메모를 남겼어요.");
        assertThat(jdbc.queryForObject("SELECT type FROM staff_notification WHERE staff_id = ?", String.class, owner)).isEqualTo("NOTE");
        assertThat(jdbc.queryForObject("SELECT message FROM staff_notification WHERE staff_id = ?", String.class, owner))
                .doesNotContain("비밀");
    }

    @Test
    @DisplayName("담당 본인이 쓴 메모, 담당이 없거나 매장 계정이 담당인 고객의 메모는 알리지 않는다")
    void should_notNotify_forOwnNoteNoOwnerOrStoreOwner() throws Exception {
        long senior = id("gn01-senior");
        write("gn01-senior", customer("내담당", senior), "{\"type\":\"CONSULT\",\"content\":\"내가 씀\"}");
        write("gn01-junior", customer("담당없음", null), "{\"type\":\"CONSULT\",\"content\":\"담당 없음\"}");
        write("gn01-junior", customer("매장담당", id("store-gn01")), "{\"type\":\"CONSULT\",\"content\":\"매장 담당\"}");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff_notification", Integer.class)).isZero();
    }
}
