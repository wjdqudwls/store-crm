package com.crmpos.product.service;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.web.PageResponse;
import com.crmpos.product.controller.ProductRequest;
import com.crmpos.product.controller.ProductResponse;
import com.crmpos.product.domain.Product;
import com.crmpos.product.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 등록·수정·조회 (PRD-01). */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String code = blankToNull(request.productCode());
        if (code != null && productRepository.existsByProductCode(code)) {
            throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED);
        }
        List<String> colors = cleaned(request.colors());
        List<String> sizes = cleaned(request.sizes());
        checkSizes(request, sizes);

        Product product = Product.create(code, request.name().trim(), request.itemType(), request.target(),
                request.listPrice());
        product.replaceSkus(colors, sizes);
        return ProductResponse.from(save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        String code = blankToNull(request.productCode());
        if (code != null && productRepository.existsByProductCodeAndIdNot(code, id)) {
            throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED);
        }
        List<String> colors = cleaned(request.colors());
        List<String> sizes = cleaned(request.sizes());
        checkSizes(request, sizes);

        product.change(code, request.name().trim(), request.itemType(), request.target(), request.listPrice());
        product.replaceSkus(colors, sizes);
        return ProductResponse.from(save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, int page, int size) {
        String escaped = escapeLike(blankToNull(keyword));
        Page<Product> result = productRepository.search(escaped,
                PageRequest.of(page, size, Sort.by("name", "id")));
        return PageResponse.of(result.getContent().stream().map(ProductResponse::from).toList(),
                page, size, result.getTotalElements());
    }

    private Product save(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 품번을 등록한 경우 UNIQUE가 막는다
            throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED);
        }
    }

    private static void checkSizes(ProductRequest request, List<String> sizes) {
        List<String> invalid = sizes.stream()
                .filter(s -> !SizeRule.isAllowed(request.itemType(), request.target(), s))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_SIZE, Map.of("invalidSizes", invalid));
        }
    }

    private static List<String> cleaned(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
