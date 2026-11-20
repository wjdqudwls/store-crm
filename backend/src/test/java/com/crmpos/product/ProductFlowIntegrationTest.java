package com.crmpos.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.support.TestAuth;
import com.crmpos.product.api.ProductApi;
import com.crmpos.product.api.SkuInfo;
import com.crmpos.support.IntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 컨트롤러부터 DB까지: 등록 → 검색 → 상세 → 수정 (PRD-01). 기본 요청은 데모 매장 계정이다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ProductFlowIntegrationTest {

    private static final String BODY = """
            {"productCode":"FLOW-1","name":"흐름 확인 코트","itemType":"OUTER","target":"WOMEN",
             "listPrice":798000,"colors":["차콜","블랙"],"sizes":["90","95"]}""";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StaffRepository staffRepository;

    @Autowired
    ProductApi productApi;

    @Autowired
    ObjectMapper objectMapper;

    private long createProduct() throws Exception {
        String json = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skus.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    @Test
    @DisplayName("등록하고 검색해서 상세까지 볼 수 있다")
    void should_registerSearchAndShow() throws Exception {
        long id = createProduct();

        mockMvc.perform(get("/api/v1/products").param("keyword", "흐름 확인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("FLOW-1"))
                .andExpect(jsonPath("$.skus.length()").value(4));
    }

    @Test
    @DisplayName("같은 품번으로 또 등록하면 409")
    void should_return409_when_sameCodeAgain() throws Exception {
        createProduct();

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_DUPLICATED"));
    }

    @Test
    @DisplayName("직원 계정으로 등록하면 403이고 상품은 만들어지지 않는다")
    void should_return403_when_staffAccountCreates() throws Exception {
        Long staffId = staffRepository.findAll().stream()
                .filter(s -> s.getRole() == StaffRole.STAFF).findFirst().orElseThrow().getId();

        mockMvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(staffId))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/products").param("keyword", "흐름 확인"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("여성 아우터에 110을 넣으면 400 INVALID_SIZE")
    void should_return400_when_sizeNotAllowed() throws Exception {
        String body = BODY.replace("\"95\"", "\"110\"");

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"))
                .andExpect(jsonPath("$.details.invalidSizes[0]").value("110"));
    }

    @Test
    @DisplayName("수정으로 사이즈를 줄이면 SKU가 줄고, 남은 SKU의 ID는 그대로다")
    void should_keepSkuIds_when_updateRemovesSizes() throws Exception {
        long id = createProduct();
        JsonNode before = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/" + id))
                .andReturn().getResponse().getContentAsString());
        long keptSkuId = before.get("skus").findParents("size").stream()
                .filter(n -> n.get("size").asText().equals("90") && n.get("color").asText().equals("차콜"))
                .findFirst().orElseThrow().get("skuId").asLong();
        String shrink = BODY.replace("\"colors\":[\"차콜\",\"블랙\"]", "\"colors\":[\"차콜\"]")
                .replace("\"sizes\":[\"90\",\"95\"]", "\"sizes\":[\"90\"]");

        mockMvc.perform(patch("/api/v1/products/" + id).contentType(MediaType.APPLICATION_JSON).content(shrink))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skus.length()").value(1))
                .andExpect(jsonPath("$.skus[0].skuId").value(keptSkuId));
    }

    @Test
    @DisplayName("없는 상품 상세는 404, 사이즈 목록은 로그인 없이 열린다")
    void should_return404AndPublicSizes() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/public/sizes")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("판매 모듈은 product.api로 SKU의 상품명·정가·색상 없음(null)을 읽는다")
    void should_readSkuInfo_when_productApi() throws Exception {
        String body = """
                {"name":"색상 없는 양말","itemType":"ACC","listPrice":9000,"sizes":["FREE"]}""";
        String json = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long skuId = objectMapper.readTree(json).get("skus").get(0).get("skuId").asLong();

        Map<Long, SkuInfo> found = productApi.findSkus(List.of(skuId, 999999L));

        assertThat(found).containsOnlyKeys(skuId);
        SkuInfo info = found.get(skuId);
        assertThat(info.productName()).isEqualTo("색상 없는 양말");
        assertThat(info.listPrice()).isEqualTo(9000);
        assertThat(info.color()).isNull();
        assertThat(info.size()).isEqualTo("FREE");
        assertThat(productApi.findSkus(List.of())).isEmpty();
    }
}
