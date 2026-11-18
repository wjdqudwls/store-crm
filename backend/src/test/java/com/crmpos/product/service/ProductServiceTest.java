package com.crmpos.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.product.controller.ProductRequest;
import com.crmpos.product.controller.ProductResponse;
import com.crmpos.product.domain.Product;
import com.crmpos.product.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductService service;

    private static ProductRequest request(String code, ItemType item, Target target, List<String> colors,
            List<String> sizes) {
        return new ProductRequest(code, "울 블렌드 롱코트", item, target, 798000L, colors, sizes);
    }

    private void saveReturnsArgument() {
        given(productRepository.saveAndFlush(any(Product.class))).willAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("색상 2 × 사이즈 3으로 등록하면 SKU 6개가 응답에 담긴다")
    void should_createSixSkus_when_valid() {
        given(productRepository.existsByProductCode("D2110")).willReturn(false);
        saveReturnsArgument();

        ProductResponse response = service.create(request("D2110", ItemType.OUTER, Target.WOMEN,
                List.of("차콜", "블랙"), List.of("90", "95", "100")));

        assertThat(response.skus()).hasSize(6);
        assertThat(response.productCode()).isEqualTo("D2110");
    }

    @Test
    @DisplayName("색상을 안 적으면 SKU 색상은 null로 보인다")
    void should_showNullColor_when_noColors() {
        saveReturnsArgument();

        ProductResponse response = service.create(request(null, ItemType.ACC, null, null, List.of("FREE")));

        assertThat(response.skus()).hasSize(1);
        assertThat(response.skus().get(0).color()).isNull();
    }

    @Test
    @DisplayName("품번을 비워 두면 중복 확인 없이 등록한다")
    void should_skipDuplicateCheck_when_codeBlank() {
        saveReturnsArgument();

        ProductResponse response = service.create(request("  ", ItemType.TOP, null, null, List.of("M")));

        assertThat(response.productCode()).isNull();
        verify(productRepository, never()).existsByProductCode(anyString());
    }

    @Test
    @DisplayName("같은 품번이 있으면 409 PRODUCT_CODE_DUPLICATED")
    void should_throw409_when_codeDuplicated() {
        given(productRepository.existsByProductCode("D2110")).willReturn(true);

        assertThatThrownBy(() -> service.create(request("D2110", ItemType.OUTER, Target.WOMEN, null, List.of("90"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_CODE_DUPLICATED));
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시에 같은 품번을 저장하다 UNIQUE에 걸려도 409로 바꾼다")
    void should_throw409_when_uniqueViolationOnSave() {
        given(productRepository.saveAndFlush(any(Product.class))).willThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(request("D2110", ItemType.OUTER, Target.WOMEN, null, List.of("90"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_CODE_DUPLICATED));
    }

    @Test
    @DisplayName("품목·대상에 없는 사이즈면 400 INVALID_SIZE이고 어떤 사이즈인지 알려 준다")
    void should_throwInvalidSize_when_sizeNotAllowed() {
        assertThatThrownBy(() -> service.create(request(null, ItemType.OUTER, Target.WOMEN, null,
                List.of("90", "110"))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SIZE);
                    assertThat(e.getDetails()).hasToString("{invalidSizes=[110]}");
                });
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("대상이 없으면 그 품목의 모든 사이즈를 쓸 수 있다")
    void should_allowAnySizeOfItem_when_noTarget() {
        saveReturnsArgument();

        ProductResponse response = service.create(request(null, ItemType.OUTER, null, null, List.of("85", "115", "4")));

        assertThat(response.skus()).hasSize(3);
    }

    @Test
    @DisplayName("수정은 자기 품번과 겹치지 않으면 통과하고, 다른 상품의 품번이면 409")
    void should_checkDuplicateExcludingSelf_when_update() {
        Product existing = Product.create("D2110", "코트", ItemType.OUTER, Target.WOMEN, 1000);
        given(productRepository.findById(7L)).willReturn(Optional.of(existing));
        given(productRepository.existsByProductCodeAndIdNot("D9999", 7L)).willReturn(true);

        assertThatThrownBy(() -> service.update(7L, request("D9999", ItemType.OUTER, Target.WOMEN, null, List.of("90"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_CODE_DUPLICATED));
    }

    @Test
    @DisplayName("없는 상품을 고치거나 보면 404 PRODUCT_NOT_FOUND")
    void should_throw404_when_productMissing() {
        given(productRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        assertThatThrownBy(() -> service.update(99L, request(null, ItemType.TOP, null, null, List.of("M"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("검색어의 LIKE 특수문자는 막아서 넘기고, 빈 검색어는 전체 검색이다")
    void should_escapeKeyword_when_search() {
        given(productRepository.search(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        service.search("50%_할인!", 0, 50);
        service.search("  ", 0, 50);

        verify(productRepository).search(org.mockito.ArgumentMatchers.eq("50!%!_할인!!"), any(Pageable.class));
        verify(productRepository).search(org.mockito.ArgumentMatchers.isNull(), any(Pageable.class));
    }
}
