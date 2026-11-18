package com.crmpos.product.repository;

import com.crmpos.product.domain.ProductSku;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {

    @Query("select s from ProductSku s join fetch s.product where s.id in :ids")
    List<ProductSku> findAllWithProductByIdIn(@Param("ids") Collection<Long> ids);
}
