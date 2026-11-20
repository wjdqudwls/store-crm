package com.crmpos.product.controller;

import com.crmpos.support.PermitAllSecurity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.security.CurrentAccountProvider;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.common.web.PageResponse;
import com.crmpos.product.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ProductController.class, SizeController.class})
@PermitAllSecurity
class ProductControllerTest {

    private static final String BODY = """
            {"productCode":"D2110","name":"울 블렌드 롱코트","itemType":"OUTER","target":"WOMEN",
             "listPrice":798000,"colors":["차콜"],"sizes":["90","95"]}""";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    @MockitoBean
    CurrentAccountProvider accountProvider;

    private static ProductResponse sample() {
        return new ProductResponse(301L, "D2110", "울 블렌드 롱코트", ItemType.OUTER, Target.WOMEN, 798000,
                List.of(new ProductResponse.SkuItem(5501L, "차콜", "90"),
                        new ProductResponse.SkuItem(5502L, null, "95")));
    }

    private void loginAs(StaffRole role) {
        given(accountProvider.current(any())).willReturn(new CurrentAccount(1L, role, 3L));
    }

    @BeforeEach
    void defaultStore() {
        loginAs(StaffRole.STORE);
    }

    @Test
    @DisplayName("매장 계정이 등록하면 201과 SKU가 든 상품을 돌려준다")
    void should_return201_when_storeCreates() throws Exception {
        given(productService.create(any())).willReturn(sample());

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.skus[0].skuId").value(5501))
                .andExpect(jsonPath("$.skus[0].color").value("차콜"))
                .andExpect(jsonPath("$.skus[1].color").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("직원 계정이 등록하면 403 ACCESS_DENIED")
    void should_return403_when_staffCreates() throws Exception {
        loginAs(StaffRole.STAFF);

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("직원 계정이 고쳐도 403")
    void should_return403_when_staffUpdates() throws Exception {
        loginAs(StaffRole.JUNIOR);

        mockMvc.perform(patch("/api/v1/products/301").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("본사 계정은 고칠 수 있다")
    void should_return200_when_hqUpdates() throws Exception {
        loginAs(StaffRole.HQ);
        given(productService.update(eq(301L), any())).willReturn(sample());

        mockMvc.perform(patch("/api/v1/products/301").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("상품명이 없고 사이즈가 비어 있으면 400 VALIDATION_FAILED와 fieldErrors")
    void should_return400_when_requiredMissing() throws Exception {
        String body = """
                {"itemType":"OUTER","listPrice":798000,"sizes":[]}""";

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='sizes')]").exists());
    }

    @Test
    @DisplayName("정가가 음수면 400")
    void should_return400_when_priceNegative() throws Exception {
        String body = BODY.replace("798000", "-1");

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("listPrice"));
    }

    @Test
    @DisplayName("같은 품번이면 409 PRODUCT_CODE_DUPLICATED")
    void should_return409_when_codeDuplicated() throws Exception {
        given(productService.create(any())).willThrow(new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_DUPLICATED"));
    }

    @Test
    @DisplayName("사이즈가 허용값이 아니면 400 INVALID_SIZE")
    void should_return400_when_invalidSize() throws Exception {
        given(productService.create(any())).willThrow(new BusinessException(ErrorCode.INVALID_SIZE));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIZE"));
    }

    @Test
    @DisplayName("없는 상품 상세는 404 PRODUCT_NOT_FOUND")
    void should_return404_when_productMissing() throws Exception {
        given(productService.get(99L)).willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("직원 계정도 상품을 검색할 수 있고 응답은 목록 형식이다")
    void should_returnPage_when_staffSearches() throws Exception {
        loginAs(StaffRole.STAFF);
        given(productService.search("코트", 0, 50)).willReturn(PageResponse.of(List.of(sample()), 0, 50, 1));

        mockMvc.perform(get("/api/v1/products").param("keyword", "코트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productCode").value("D2110"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("한 번에 100개를 넘겨 달라고 하면 400")
    void should_return400_when_sizeTooBig() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("사이즈 목록은 로그인 없이 열린다")
    void should_returnSizes_when_publicRequest() throws Exception {
        given(accountProvider.current(any())).willThrow(new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));

        mockMvc.perform(get("/api/v1/public/sizes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.itemType=='ACC')].sizes[0]").value("FREE"))
                .andExpect(jsonPath("$[?(@.itemType=='TOP' && @.target==null)]").exists());
    }
}
