package com.crmpos.product.api;

import java.util.Collection;
import java.util.Map;

/** 다른 모듈이 상품 데이터를 읽는 창구 (ADR-012). 없는 SKU는 결과에 없다. */
public interface ProductApi {

    Map<Long, SkuInfo> findSkus(Collection<Long> skuIds);
}
