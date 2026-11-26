package com.crmpos.product.repository;

import com.crmpos.product.domain.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    /** 이미 등록된 품번만 골라 돌려준다 (엑셀 올리기의 중복 검사). */
    @Query("select p.productCode from Product p where p.productCode in :codes")
    List<String> findExistingCodes(@Param("codes") Collection<String> codes);

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
