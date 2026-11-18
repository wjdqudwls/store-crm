package com.crmpos.product.controller;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.product.domain.Product;
import java.util.List;

/** 상품 응답 (03-api.md 4.12 상품 검색 항목). 색상이 없는 SKU의 color는 null이다. */
public record ProductResponse(Long id, String productCode, String name, ItemType itemType, Target target,
        long listPrice, List<SkuItem> skus) {

    public record SkuItem(Long skuId, String color, String size) {
    }

    public static ProductResponse from(Product product) {
        List<SkuItem> skus = product.getSkus().stream()
                .map(s -> new SkuItem(s.getId(), s.getColor().isEmpty() ? null : s.getColor(), s.getSize()))
                .toList();
        return new ProductResponse(product.getId(), product.getProductCode(), product.getName(),
                product.getItemType(), product.getTarget(), product.getListPrice(), skus);
    }
}
