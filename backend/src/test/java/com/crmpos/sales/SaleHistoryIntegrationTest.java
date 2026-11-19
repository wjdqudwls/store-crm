package com.crmpos.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
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
import java.time.ZoneId;
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

/** 판매 내역·영수증 상세·고객 구매이력·하루 판매 요약을 실제 DB로 확인한다 (SAL-05, CUS-03, ADM-03). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
class SaleHistoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

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

    private long customer(String name, String hash) {
        CustomerDraft draft = CustomerDraft.builder().name(name).nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1}).phoneHash(hash).phoneLast4("4821").privacyAgreed(true).age14Confirmed(true)
                .homeStoreId(1L).source(CustomerSource.STORE).build();
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(customer.getId(), 1, now));
        return customer.getId();
    }

    private void returnQty(long[] sale, int qty) throws Exception {
        kit.send(kit.as(post("/api/v1/sales/" + sale[0] + "/returns").header("Idempotency-Key", SalesTestKit.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"lines\":[{\"lineId\":%d,\"qty\":%d}]}".formatted(
                        kit.staffId("gn01-senior"), sale[1], qty)), null)).andExpect(status().isCreated());
    }

    // ---- 판매 내역

    @Test
    @DisplayName("매장 계정은 자기 매장 판매만 최신순으로 보고, 고객·직원 이름이 함께 온다")
    void should_listOwnStoreSalesNewestFirst() throws Exception {
        long customerId = customer("내역 김하늘", "a".repeat(64));
        kit.createSale(null, "gn01-senior", "첫째 양말", 1, 1000, null);
        kit.createSale(null, "gn01-senior", "둘째 양말", 2, 4000, customerId);
        kit.createSale("store-hd01", "hd01-senior", "홍대 양말", 1, 500, null);

        kit.send(kit.as(get("/api/v1/sales"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].totalAmount").value(4000))
                .andExpect(jsonPath("$.content[0].totalQty").value(2))
                .andExpect(jsonPath("$.content[0].customerName").value("내역 김하늘"))
                .andExpect(jsonPath("$.content[0].sellerName").value("강남 시니어"))
                .andExpect(jsonPath("$.content[0].kind").value("NORMAL"))
                .andExpect(jsonPath("$.content[0].originReceiptNo").value((Object) null))
                .andExpect(jsonPath("$.content[1].customerId").value((Object) null))
                .andExpect(jsonPath("$.content[1].customerName").value((Object) null));
    }

    @Test
    @DisplayName("본사는 매장을 골라야 보고(없으면 400), 직원 계정은 403, 페이지로 나눠 준다")
    void should_checkRolesAndPage() throws Exception {
        kit.createSale(null, "gn01-senior", "양말1", 1, 1000, null);
        kit.createSale(null, "gn01-senior", "양말2", 1, 1000, null);
        long gangnam = storeRepository.findByCode("GN01").orElseThrow().getId();

        kit.send(kit.as(get("/api/v1/sales"), "hq")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("storeId"));
        kit.send(kit.as(get("/api/v1/sales").param("storeId", String.valueOf(gangnam)), "hq"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        kit.send(kit.as(get("/api/v1/sales"), "gn01-senior")).andExpect(status().isForbidden());
        kit.send(kit.as(get("/api/v1/sales").param("size", "1"), null)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        kit.send(kit.as(get("/api/v1/sales").param("size", "101"), null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기간(from·to)으로 거르고, 기간을 안 주면 최근 30일, 거꾸로면 400")
    void should_filterByPeriod() throws Exception {
        long[] old = kit.createSale(null, "gn01-senior", "오래된 양말", 1, 1000, null);
        kit.createSale(null, "gn01-senior", "오늘 양말", 1, 1000, null);
        jdbc.update("UPDATE sale SET sold_at = ? WHERE id = ?", LocalDateTime.now().minusDays(10), old[0]);

        kit.send(kit.as(get("/api/v1/sales"), null)).andExpect(jsonPath("$.totalElements").value(2));
        kit.send(kit.as(get("/api/v1/sales").param("from", TODAY.toString()).param("to", TODAY.toString()), null))
                .andExpect(jsonPath("$.totalElements").value(1));
        kit.send(kit.as(get("/api/v1/sales").param("from", TODAY.toString()).param("to", TODAY.minusDays(1).toString()),
                null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
    }

    // ---- 영수증 상세

    @Test
    @DisplayName("영수증 상세에 라인별 반품 수량·금액과 반품 내역(처리 직원 이름), 매장·고객 이름이 온다")
    void should_showReceiptDetailWithReturns() throws Exception {
        long customerId = customer("상세 박도윤", "b".repeat(64));
        long[] sale = kit.createSale(null, "gn01-senior", "상세 양말", 3, 100, customerId);
        returnQty(sale, 1);

        kit.send(kit.as(get("/api/v1/sales/" + sale[0]), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.store.name").value("강남점"))
                .andExpect(jsonPath("$.customerName").value("상세 박도윤"))
                .andExpect(jsonPath("$.sellerName").value("강남 시니어"))
                .andExpect(jsonPath("$.status").value("PARTIAL_RETURNED"))
                .andExpect(jsonPath("$.lines[0].returnedQty").value(1))
                .andExpect(jsonPath("$.lines[0].returnedAmount").value(33))
                .andExpect(jsonPath("$.lines[0].color").value((Object) null))
                .andExpect(jsonPath("$.returns[0].kind").value("RETURN"))
                .andExpect(jsonPath("$.returns[0].amount").value(33))
                .andExpect(jsonPath("$.returns[0].staffName").value("강남 시니어"))
                .andExpect(jsonPath("$.returns[0].newSaleId").value((Object) null))
                .andExpect(jsonPath("$.totalAmount").value(100));
    }

    @Test
    @DisplayName("다른 매장 영수증도 보기만 되고, 없는 영수증은 404, 직원 계정은 403")
    void should_showOtherStoreReceiptReadOnly() throws Exception {
        long[] sale = kit.createSale(null, "gn01-senior", "강남 양말", 1, 1000, null);

        kit.send(kit.as(get("/api/v1/sales/" + sale[0]), "store-hd01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.store.name").value("강남점"));
        kit.send(kit.as(get("/api/v1/sales/999999"), null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
        kit.send(kit.as(get("/api/v1/sales/" + sale[0]), "gn01-senior")).andExpect(status().isForbidden());
    }

    // ---- 구매이력

    @Test
    @DisplayName("고객 구매이력은 기록 취소를 빼고 최신순이며, keyword가 있으면 그 말이 든 줄만 담는다")
    void should_listPurchasesWithKeyword() throws Exception {
        long customerId = customer("구매 김하늘", "c".repeat(64));
        kit.createSale(null, "gn01-senior", "울 코트", 1, 9000, customerId);
        kit.createSale("store-hd01", "hd01-senior", "면 양말", 2, 2000, customerId);
        long[] voided = kit.createSale(null, "gn01-senior", "취소될 코트", 1, 1000, customerId);
        jdbc.update("UPDATE sale SET status = 'VOIDED' WHERE id = ?", voided[0]);
        String url = "/api/v1/customers/" + customerId + "/purchases";

        kit.send(kit.as(get(url), null)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].storeName").value("홍대점"))
                .andExpect(jsonPath("$.content[1].storeName").value("강남점"))
                .andExpect(jsonPath("$.content[0].lines[0].productName").value("면 양말"));
        kit.send(kit.as(get(url).param("keyword", "코트"), null)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lines.length()").value(1))
                .andExpect(jsonPath("$.content[0].lines[0].productName").value("울 코트"));
        kit.send(kit.as(get(url).param("keyword", "없는말"), "gn01-senior")).andExpect(jsonPath("$.totalElements").value(0));
        kit.send(kit.as(get("/api/v1/customers/999999/purchases"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---- 하루 판매 요약

    @Test
    @DisplayName("하루 판매 요약: 기록 취소를 뺀 수, 마감 여부, 매장 계정은 자기 매장 1개, 본사는 전 매장")
    void should_summarizeDay() throws Exception {
        kit.createSale(null, "gn01-senior", "양말1", 1, 1000, null);
        kit.createSale(null, "gn01-senior", "양말2", 1, 1000, null);
        long[] cancelled = kit.createSale(null, "gn01-senior", "양말3", 1, 1000, null);
        jdbc.update("UPDATE sale SET status = 'VOIDED' WHERE id = ?", cancelled[0]);
        kit.createSale("store-hd01", "hd01-senior", "홍대 양말", 1, 500, null);
        long gangnam = storeRepository.findByCode("GN01").orElseThrow().getId();

        kit.send(kit.as(get("/api/v1/sales/daily-summary"), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].storeId").value(gangnam))
                .andExpect(jsonPath("$[0].date").value(TODAY.toString()))
                .andExpect(jsonPath("$[0].saleCount").value(2))
                .andExpect(jsonPath("$[0].closed").value(false))
                .andExpect(jsonPath("$[0].closedAt").value((Object) null));
        // 매장 계정이 다른 매장 ID를 보내도 자기 매장이다
        kit.send(kit.as(get("/api/v1/sales/daily-summary").param("storeId", "999"), null))
                .andExpect(jsonPath("$[0].storeId").value(gangnam));

        kit.send(kit.as(post("/api/v1/day-closes"), null)).andExpect(status().isCreated());
        kit.send(kit.as(get("/api/v1/sales/daily-summary"), null)).andExpect(jsonPath("$[0].closed").value(true))
                .andExpect(jsonPath("$[0].closedAt").isNotEmpty());

        String all = kit.send(kit.as(get("/api/v1/sales/daily-summary"), "hq")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = kit.mapper.readTree(all);
        assertThat(items).hasSize(2);
        assertThat(items.findValues("saleCount")).extracting(JsonNode::asLong).containsExactlyInAnyOrder(2L, 1L);
        kit.send(kit.as(get("/api/v1/sales/daily-summary").param("storeId", String.valueOf(gangnam)), "hq"))
                .andExpect(jsonPath("$.length()").value(1));
        kit.send(kit.as(get("/api/v1/sales/daily-summary").param("date", TODAY.minusDays(1).toString()), null))
                .andExpect(jsonPath("$[0].saleCount").value(0)).andExpect(jsonPath("$[0].closed").value(false));
        kit.send(kit.as(get("/api/v1/sales/daily-summary").param("storeId", "999999"), "hq"))
                .andExpect(status().isNotFound());
        kit.send(kit.as(get("/api/v1/sales/daily-summary"), "gn01-senior")).andExpect(status().isForbidden());
    }
}
