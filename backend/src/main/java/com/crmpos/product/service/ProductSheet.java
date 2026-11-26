package com.crmpos.product.service;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 상품 엑셀 폼의 모양 (PRD-02, ADR-015). 폼 만들기와 읽기가 같이 써서 시트·열 이름과 값 표시가 어긋나지 않게 한다. */
final class ProductSheet {

    static final String DATA_SHEET = "상품";
    static final String GUIDE_SHEET = "작성 방법";
    static final List<String> HEADERS = List.of("품번", "상품명", "품목", "대상", "정가", "색상", "사이즈");
    static final int MAX_ROWS = 1000;
    /** 상품명이 이 글로 시작하는 행은 작성 예시라 저장하지 않는다. */
    static final String EXAMPLE_PREFIX = "(예시)";

    static final Map<String, ItemType> ITEM_TYPES = new LinkedHashMap<>();
    static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    static {
        ITEM_TYPES.put("아우터", ItemType.OUTER);
        ITEM_TYPES.put("상의", ItemType.TOP);
        ITEM_TYPES.put("하의", ItemType.BOTTOM);
        ITEM_TYPES.put("신발", ItemType.SHOES);
        ITEM_TYPES.put("액세서리", ItemType.ACC);
        TARGETS.put("여성", Target.WOMEN);
        TARGETS.put("남성", Target.MEN);
        TARGETS.put("아동", Target.KIDS);
        TARGETS.put("공용", Target.UNISEX);
    }

    private ProductSheet() {
    }

    static String itemTypeLabel(ItemType type) {
        return ITEM_TYPES.entrySet().stream().filter(e -> e.getValue() == type).findFirst().orElseThrow().getKey();
    }

    static String targetLabel(Target target) {
        return TARGETS.entrySet().stream().filter(e -> e.getValue() == target).findFirst().orElseThrow().getKey();
    }
}
