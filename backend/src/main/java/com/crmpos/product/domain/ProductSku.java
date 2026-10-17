package com.crmpos.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 색상·사이즈 단위 판매 단위. 사이즈 허용값은 코드에서 검사한다 (02-erd.md 3.2). */
@Entity
@Table(name = "product_sku")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 선택. 색상이 없으면 null 대신 빈 문자열로 저장해 (product_id, color, size) UNIQUE가 동작하게 한다. */
    @Column(nullable = false, length = 30)
    private String color;

    @Column(nullable = false, length = 20)
    private String size;
}
