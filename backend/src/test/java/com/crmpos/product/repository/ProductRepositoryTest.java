package com.crmpos.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.product.domain.Product;
import com.crmpos.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/** 저장한 뒤 다시 조회해서 확인한다 (실제 MariaDB). */
@IntegrationTest
@Transactional
class ProductRepositoryTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager em;

    private Product saved(String code, String name, String... sizes) {
        Product product = Product.create(code, name, ItemType.OUTER, Target.WOMEN, 798000);
        product.replaceSkus(List.of("차콜"), List.of(sizes));
        return productRepository.saveAndFlush(product);
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("상품을 저장하고 다시 읽으면 SKU까지 같다")
    void should_loadProductWithSkus_when_saved() {
        Long id = saved("T-0001", "테스트 코트", "90", "95").getId();
        reload();

        Product found = productRepository.findById(id).orElseThrow();

        assertThat(found.getProductCode()).isEqualTo("T-0001");
        assertThat(found.getSkus()).extracting(s -> s.getSize()).containsExactlyInAnyOrder("90", "95");
        assertThat(found.getSkus()).allSatisfy(s -> assertThat(s.getId()).isNotNull());
    }

    @Test
    @DisplayName("같은 품번을 또 저장하면 UNIQUE가 막는다")
    void should_rejectDuplicateCode_when_savedTwice() {
        saved("T-0002", "첫 코트", "90");

        assertThatThrownBy(() -> saved("T-0002", "둘째 코트", "90")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("품번이 없는 상품은 여러 개 저장할 수 있다")
    void should_allowManyProducts_when_codeNull() {
        saved(null, "품번 없는 코트 1", "90");
        saved(null, "품번 없는 코트 2", "90");
        reload();

        assertThat(productRepository.search("품번 없는 코트", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("상품명이나 품번의 일부로 찾고 대소문자는 구분하지 않는다")
    void should_searchByNameOrCode_when_keyword() {
        saved("ZZ-LONG-1", "울 블렌드 롱코트", "90");
        saved("ZZ-SHORT-2", "캐시미어 숏자켓", "90");
        reload();

        assertThat(names("롱코트")).containsExactly("울 블렌드 롱코트");
        assertThat(names("zz-short")).containsExactly("캐시미어 숏자켓");
        assertThat(names("ZZ-")).hasSize(2);
        assertThat(names("없는말")).isEmpty();
    }

    @Test
    @DisplayName("검색어에 퍼센트나 밑줄이 있어도 글자 그대로 찾는다")
    void should_treatWildcardsLiterally_when_escaped() {
        saved("ZZ-P1", "50%할인 코트", "90");
        saved("ZZ-P2", "50번 코트", "90");
        reload();

        assertThat(names("50!%")).containsExactly("50%할인 코트");
    }

    @Test
    @DisplayName("검색어가 없으면 전부, 페이지로 나눠 돌려준다")
    void should_pageResults_when_noKeyword() {
        saved("ZZ-A1", "가 코트", "90");
        saved("ZZ-A2", "나 코트", "90");
        saved("ZZ-A3", "다 코트", "90");
        reload();

        var page = productRepository.search(null, PageRequest.of(0, 2, Sort.by("name", "id")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.getTotalPages()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("사이즈를 줄여 저장하면 빠진 SKU 행은 지워진다")
    void should_deleteRemovedSkus_when_replaced() {
        Product product = saved("T-0003", "줄이는 코트", "90", "95", "100");
        Long id = product.getId();
        reload();

        Product loaded = productRepository.findById(id).orElseThrow();
        loaded.replaceSkus(List.of("차콜"), List.of("90"));
        productRepository.saveAndFlush(loaded);
        reload();

        assertThat(productRepository.findById(id).orElseThrow().getSkus()).hasSize(1);
    }

    private List<String> names(String keyword) {
        return productRepository.search(keyword, PageRequest.of(0, 10, Sort.by("name"))).getContent().stream()
                .map(Product::getName).toList();
    }
}
