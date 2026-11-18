package com.crmpos.product.repository;

import com.crmpos.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    boolean existsByProductCodeAndIdNot(String productCode, Long id);

    /** 상품명·품번 검색. keyword는 LIKE 특수문자를 '!'로 막은 값이다. 없으면 전체. */
    @Query("""
            select p from Product p
            where :keyword is null
               or p.name like concat('%', :keyword, '%') escape '!'
               or p.productCode like concat('%', :keyword, '%') escape '!'
            """)
    Page<Product> search(@Param("keyword") String keyword, Pageable pageable);
}
