package com.crmpos.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.PageResponse;
import com.crmpos.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 상품 등록·수정(매장·본사)과 조회(로그인한 누구나) (PRD-01). */
@Tag(name = "상품")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 검색", description = "상품명·품번(대소문자 구분 없음)으로 찾고 색상·사이즈(SKU)를 함께 준다. 로그인한 모든 계정이 쓴다. size는 1~100.")
    @GetMapping
    public PageResponse<ProductResponse> search(CurrentAccount account,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return productService.search(keyword, page, size);
    }

    @Operation(summary = "상품 상세", description = "SKU를 포함한 상품 1건. 없으면 PRODUCT_NOT_FOUND 404.")
    @GetMapping("/{id}")
    public ProductResponse get(CurrentAccount account, @PathVariable Long id) {
        return productService.get(id);
    }

    @Operation(summary = "상품 등록", description = "매장 계정과 본사가 쓴다. 허용되지 않은 사이즈는 INVALID_SIZE 400, 같은 품번은 PRODUCT_CODE_DUPLICATED 409. 성공하면 201.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(CurrentAccount account, @Valid @RequestBody ProductRequest request) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        return productService.create(request);
    }

    @Operation(summary = "상품 수정", description = "본문 전체로 바꾼다. 사이즈·색상을 빼도 과거 판매 기록은 스냅샷이라 그대로다. 오류는 등록과 같다.")
    @PatchMapping("/{id}")
    public ProductResponse update(CurrentAccount account, @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        return productService.update(id, request);
    }
}
