package com.crmpos.sales.domain;

/** 판매 종류 (02-erd.md 3.3). 교환·정정 판매는 origin_sale_id로 원 판매를 가리킨다. */
public enum SaleKind {
    /** 일반 판매. */
    NORMAL,
    /** 교환으로 받은 새 상품. */
    EXCHANGE,
    /** 정정으로 다시 기록한 판매. */
    CORRECTION
}
