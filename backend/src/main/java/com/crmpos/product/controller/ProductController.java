package com.crmpos.product.controller;

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
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> search(CurrentAccount account,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return productService.search(keyword, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(CurrentAccount account, @PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(CurrentAccount account, @Valid @RequestBody ProductRequest request) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        return productService.create(request);
    }

    @PatchMapping("/{id}")
    public ProductResponse update(CurrentAccount account, @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        return productService.update(id, request);
    }
}
