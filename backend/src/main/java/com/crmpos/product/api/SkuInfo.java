package com.crmpos.product.api;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;

/** 판매 모듈이 읽는 SKU 정보. 색상이 없으면 color는 null이다. */
public record SkuInfo(Long skuId, Long productId, String productCode, String productName, ItemType itemType,
        Target target, String color, String size, long listPrice) {
}
