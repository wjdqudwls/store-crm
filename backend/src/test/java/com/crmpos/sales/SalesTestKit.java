package com.crmpos.sales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.support.TestAuth;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 판매 통합 테스트들이 같이 쓰는 도우미: 계정 헤더, 판매 만들기, 정리. 가상 데이터만 쓴다. */
final class SalesTestKit {

    final MockMvc mockMvc;
    final JdbcTemplate jdbc;
    final StaffRepository staffRepository;
    final JsonMapper mapper = JsonMapper.builder().build();

    SalesTestKit(MockMvc mockMvc, JdbcTemplate jdbc, StaffRepository staffRepository) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.staffRepository = staffRepository;
    }

    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM sale_return_line");
        jdbc.update("DELETE FROM sale_return");
        jdbc.update("DELETE FROM sale_line");
        jdbc.update("UPDATE sale SET root_sale_id = NULL, origin_sale_id = NULL");
        jdbc.update("DELETE FROM sale");
        jdbc.update("DELETE FROM receipt_sequence");
        jdbc.update("DELETE FROM store_day_close");
        jdbc.update("DELETE FROM customer_member");
        jdbc.update("DELETE FROM customer_summary");
        jdbc.update("DELETE FROM customer");
    }

    long staffId(String loginId) {
        return staffRepository.findAll().stream().filter(s -> s.getLoginId().equals(loginId)).findFirst()
                .orElseThrow().getId();
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String loginId) {
        if (loginId != null) {
            builder.header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(staffId(loginId)));
        }
        return builder;
    }

    /** 직접 입력 라인 하나로 판매를 만들고 {saleId, lineId}를 돌려준다. storeAccount가 null이면 강남점 매장 계정이다. */
    long[] createSale(String storeAccount, String seller, String productName, int qty, long amount, Long customerId)
            throws Exception {
        String body = "{\"sellerStaffId\":%d%s,\"lines\":[{\"productName\":\"%s\",\"itemType\":\"ACC\",\"size\":\"FREE\",\"qty\":%d,\"lineAmount\":%d}]}"
                .formatted(staffId(seller), customerId == null ? "" : ",\"customerId\":" + customerId, productName, qty,
                        amount);
        String json = mockMvc.perform(as(post("/api/v1/sales").header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body), storeAccount))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(json);
        return new long[] {node.get("id").asLong(), node.get("lines").get(0).get("lineId").asLong()};
    }

    ResultActions send(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder);
    }
}
