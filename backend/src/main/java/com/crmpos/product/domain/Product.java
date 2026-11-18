package com.crmpos.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.crmpos.common.type.ItemType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.BatchSize;
import com.crmpos.common.type.Target;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품. 품번과 대상은 선택이다 (PRD-01·02, ADR-014). */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 선택. 없으면 null이고, null은 여러 개여도 UNIQUE에 걸리지 않는다. */
    @Column(length = 20)
    private String productCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Target target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType itemType;

    @Column(nullable = false)
    private long listPrice;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<ProductSku> skus = new ArrayList<>();

    public static Product create(String productCode, String name, ItemType itemType, Target target, long listPrice) {
        Product product = new Product();
        product.change(productCode, name, itemType, target, listPrice);
        return product;
    }

    public void change(String productCode, String name, ItemType itemType, Target target, long listPrice) {
        this.productCode = productCode;
        this.name = name;
        this.itemType = itemType;
        this.target = target;
        this.listPrice = listPrice;
    }

    /**
     * 색상 × 사이즈 조합으로 SKU를 맞춘다. 색상이 없으면 빈 문자열 하나로 본다 (02-erd.md 3.2).
     * 이미 있는 조합은 그대로 두고(ID 유지), 새 조합은 더하고, 빠진 조합은 지운다. 과거 판매는 스냅샷이라 영향이 없다.
     */
    public void replaceSkus(List<String> colors, List<String> sizes) {
        Set<String> wantedColors = new LinkedHashSet<>(colors.isEmpty() ? List.of("") : colors);
        Set<String> wantedSizes = new LinkedHashSet<>(sizes);
        skus.removeIf(sku -> !wantedColors.contains(sku.getColor()) || !wantedSizes.contains(sku.getSize()));
        for (String color : wantedColors) {
            for (String size : wantedSizes) {
                boolean exists = skus.stream().anyMatch(s -> s.getColor().equals(color) && s.getSize().equals(size));
                if (!exists) {
                    skus.add(ProductSku.of(this, color, size));
                }
            }
        }
    }
}
