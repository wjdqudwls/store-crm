package com.crmpos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 상담 메모를 컨트롤러부터 DB까지 확인한다 (MEM-01~03, CAL-02). 테스트마다 롤백한다. 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class NoteIntegrationTest {

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
    long customerId;

    private long id(String loginId) {
        return jdbc.queryForObject("SELECT id FROM staff WHERE login_id = ?", Long.class, loginId);
    }

    private String as(String loginId) {
        return TestAuth.bearer(id(loginId));
    }

    @BeforeEach
    void customer() {
        CustomerDraft draft = CustomerDraft.builder().name("메모 고객").nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1}).phoneHash("e".repeat(64)).phoneLast4("4821").privacyAgreed(true)
                .age14Confirmed(true).homeStoreId(1L).source(CustomerSource.STORE).build();
        LocalDateTime now = LocalDateTime.now();
        Customer saved = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(saved.getId(), 1, now));
        customerId = saved.getId();
    }

    private ResultActions write(String account, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/customers/" + customerId + "/notes").header(HttpHeaders.AUTHORIZATION, as(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String storeNote(String content) {
        return "{\"type\":\"CONSULT\",\"content\":\"%s\",\"staffId\":%d}".formatted(content, id("gn01-senior"));
    }

    private long createNote(String account, String body) throws Exception {
        String json = write(account, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    // --- 작성 ---

    @Test
    @DisplayName("매장 계정이 작성 직원을 골라 쓰면 201과 메모 항목, 작성 직원·기록한 계정이 따로 저장된다")
    void should_create201_when_storeAccountPicksAuthor() throws Exception {
        write("store-gn01", storeNote("코트 수선 문의")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CONSULT")).andExpect(jsonPath("$.content").value("코트 수선 문의"))
                .andExpect(jsonPath("$.staffId").value(id("gn01-senior"))).andExpect(jsonPath("$.staffName").value("강남 시니어"))
                .andExpect(jsonPath("$.editable").value(true)).andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.followUpId").doesNotExist()).andExpect(jsonPath("$.createdAt").exists());

        var row = jdbc.queryForMap("SELECT staff_id, recorded_by, type, customer_id FROM consult_note");
        assertThat(row.get("staff_id")).isEqualTo(id("gn01-senior"));
        assertThat(row.get("recorded_by")).isEqualTo(id("store-gn01"));
        assertThat(row.get("customer_id")).isEqualTo(customerId);
    }

    @Test
    @DisplayName("직원 개인 계정은 본인이 작성 직원이고 본문의 staffId는 무시한다")
    void should_useSelfAsAuthor_when_employee() throws Exception {
        write("gn01-junior", storeNote("착용감 좋아하심")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").value(id("gn01-junior")));
    }

    @Test
    @DisplayName("매장 계정이 작성 직원을 빼면 400 fieldErrors(staffId), 다른 매장 직원이면 400")
    void should_return400_when_authorMissingOrOtherStore() throws Exception {
        write("store-gn01", "{\"type\":\"CONSULT\",\"content\":\"메모\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
        write("store-gn01", "{\"type\":\"CONSULT\",\"content\":\"메모\",\"staffId\":%d}".formatted(id("hd01-senior")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("staffId"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consult_note", Integer.class)).isZero();
    }

    @Test
    @DisplayName("연락(CALL)은 결과와 채널이 필수이고, 있으면 저장된다")
    void should_requireResultAndChannel_when_call() throws Exception {
        write("gn01-junior", "{\"type\":\"CALL\",\"content\":\"통화\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        write("gn01-junior", "{\"type\":\"CALL\",\"content\":\"부재중\",\"callResult\":\"ABSENT\",\"channel\":\"KAKAO\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.callResult").value("ABSENT"))
                .andExpect(jsonPath("$.channel").value("KAKAO"));
    }

    @Test
    @DisplayName("내용이 비었거나 1,000자를 넘으면 400, 1,000자는 통과 (경계)")
    void should_validateContentLength() throws Exception {
        write("gn01-junior", "{\"type\":\"ETC\",\"content\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"));
        write("gn01-junior", "{\"type\":\"ETC\",\"content\":\"" + "가".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest());
        write("gn01-junior", "{\"type\":\"ETC\",\"content\":\"" + "가".repeat(1000) + "\"}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("방문 예약 메모는 후속 연락(B4-3) 전까지 400, 본사는 쓸 수 없고(403), 없는 고객은 404")
    void should_rejectReservationHqAndMissingCustomer() throws Exception {
        write("gn01-junior", "{\"type\":\"RESERVATION\",\"content\":\"예약\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
        write("hq", "{\"type\":\"ETC\",\"content\":\"메모\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/customers/999999/notes").header(HttpHeaders.AUTHORIZATION, as("gn01-junior"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ETC\",\"content\":\"메모\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    // --- 목록 ---

    @Test
    @DisplayName("목록은 최신순이고 페이지 정보가 있으며, 판매 이벤트로 받은 구매 메모(saleId)도 함께 나온다")
    void should_listNewestFirst_withEventNote() throws Exception {
        createNote("gn01-junior", "{\"type\":\"CONSULT\",\"content\":\"첫 메모\"}");
        createNote("gn01-senior", "{\"type\":\"ETC\",\"content\":\"둘째 메모\"}");
        jdbc.update("INSERT INTO consult_note (customer_id, staff_id, type, recorded_by, content, sale_id, created_at, updated_at) "
                + "VALUES (?, ?, 'PURCHASE', ?, '롱코트 구매', 88231, '2020-01-01 10:00:00', '2020-01-01 10:00:00')", customerId,
                id("gn01-senior"), id("store-gn01"));

        mockMvc.perform(get("/api/v1/customers/" + customerId + "/notes").header(HttpHeaders.AUTHORIZATION, as("hq")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].content").value("둘째 메모"))
                .andExpect(jsonPath("$.content[1].content").value("첫 메모"))
                .andExpect(jsonPath("$.content[2].type").value("PURCHASE")).andExpect(jsonPath("$.content[2].saleId").value(88231))
                .andExpect(jsonPath("$.content[0].staffName").value("강남 시니어"));
    }

    @Test
    @DisplayName("editable: 본인 메모만 직원에게, 같은 매장 메모는 매장 계정에게, 다른 매장 매장 계정에는 false, 본사는 전부 true")
    void should_markEditablePerAccount() throws Exception {
        createNote("gn01-junior", "{\"type\":\"ETC\",\"content\":\"주니어 메모\"}");

        ResultActionsHolder.assertEditable(mockMvc, customerId, as("gn01-junior"), true);
        ResultActionsHolder.assertEditable(mockMvc, customerId, as("gn01-senior"), false);
        ResultActionsHolder.assertEditable(mockMvc, customerId, as("store-gn01"), true);
        ResultActionsHolder.assertEditable(mockMvc, customerId, as("store-hd01"), false);
        ResultActionsHolder.assertEditable(mockMvc, customerId, as("hq"), true);
    }

    /** 목록의 첫 메모 editable을 확인하는 작은 도우미. */
    static final class ResultActionsHolder {
        static void assertEditable(MockMvc mvc, long customerId, String token, boolean expected) throws Exception {
            mvc.perform(get("/api/v1/customers/" + customerId + "/notes").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].editable").value(expected));
        }
    }

    // --- 수정·삭제 ---

    private ResultActions edit(String account, long noteId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/notes/" + noteId).header(HttpHeaders.AUTHORIZATION, as(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("작성 직원 본인·그 매장의 매장 계정·본사는 내용을 고치고 updatedAt이 생긴다")
    void should_edit_when_authorStoreOrHq() throws Exception {
        long noteId = createNote("gn01-junior", "{\"type\":\"CONSULT\",\"content\":\"원래\"}");

        edit("gn01-junior", noteId, "{\"content\":\"본인이 고침\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("본인이 고침")).andExpect(jsonPath("$.type").value("CONSULT"));
        edit("store-gn01", noteId, "{\"content\":\"매장이 고침\"}").andExpect(status().isOk());
        edit("hq", noteId, "{\"content\":\"본사가 고침\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").exists());
        em.flush(); // 같은 트랜잭션의 JDBC 조회가 JPA 변경을 보게 한다
        assertThat(jdbc.queryForObject("SELECT content FROM consult_note WHERE id = ?", String.class, noteId))
                .isEqualTo("본사가 고침");
    }

    @Test
    @DisplayName("남의 메모는 직원이 못 고치고(403), 다른 매장의 매장 계정도 못 고친다(403). 없는 메모는 404, 입력 오류는 권한 뒤 400")
    void should_forbidOthers_and404_and400AfterPermission() throws Exception {
        long noteId = createNote("gn01-junior", "{\"type\":\"CONSULT\",\"content\":\"원래\"}");

        edit("gn01-senior", noteId, "{\"content\":\"남이 고침\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        edit("store-hd01", noteId, "{\"content\":\"다른 매장\"}").andExpect(status().isForbidden());
        // 권한이 없으면 입력이 틀려도 403이 먼저다
        edit("gn01-senior", noteId, "{\"content\":\"\"}").andExpect(status().isForbidden());
        edit("gn01-junior", 999999, "{\"content\":\"x\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
        edit("gn01-junior", noteId, "{\"content\":\"\"}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT content FROM consult_note WHERE id = ?", String.class, noteId))
                .isEqualTo("원래");
    }

    @Test
    @DisplayName("삭제는 수정과 같은 권한이고 성공하면 204, 지워진 메모는 목록에서 사라진다")
    void should_delete_withSamePermission() throws Exception {
        long noteId = createNote("gn01-junior", "{\"type\":\"CONSULT\",\"content\":\"지울 메모\"}");

        mockMvc.perform(delete("/api/v1/notes/" + noteId).header(HttpHeaders.AUTHORIZATION, as("gn01-senior")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/notes/" + noteId).header(HttpHeaders.AUTHORIZATION, as("gn01-junior")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/notes/" + noteId).header(HttpHeaders.AUTHORIZATION, as("gn01-junior")))
                .andExpect(status().isNotFound());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consult_note", Integer.class)).isZero();
    }

    @Test
    @DisplayName("메모 내용은 로그에 남지 않는다 (작성·수정·검증 실패)")
    void should_notLogNoteContent(CapturedOutput output) throws Exception {
        long noteId = createNote("gn01-junior", "{\"type\":\"CONSULT\",\"content\":\"비밀내용ALPHA\"}");
        edit("gn01-junior", noteId, "{\"content\":\"비밀내용BETA\"}").andExpect(status().isOk());
        write("gn01-junior", "{\"type\":\"CALL\",\"content\":\"비밀내용GAMMA\"}").andExpect(status().isBadRequest());

        assertThat(output.getAll()).doesNotContain("비밀내용");
    }
}
