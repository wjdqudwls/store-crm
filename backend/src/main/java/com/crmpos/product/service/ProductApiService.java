package com.crmpos.product.service;

import com.crmpos.product.api.ProductApi;
import com.crmpos.product.api.SkuInfo;
import com.crmpos.product.domain.ProductSku;
import com.crmpos.product.repository.ProductSkuRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductApiService implements ProductApi {

    private final ProductSkuRepository skuRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SkuInfo> findSkus(Collection<Long> skuIds) {
        Map<Long, SkuInfo> result = new LinkedHashMap<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return result;
        }
        for (ProductSku sku : skuRepository.findAllWithProductByIdIn(skuIds)) {
            var product = sku.getProduct();
            result.put(sku.getId(), new SkuInfo(sku.getId(), product.getId(), product.getProductCode(),
                    product.getName(), product.getItemType(), product.getTarget(),
                    sku.getColor().isEmpty() ? null : sku.getColor(), sku.getSize(), product.getListPrice()));
        }
        return result;
    }
}
