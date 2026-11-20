package com.crmpos.customer.domain;

/** 할 일의 종류 (02-erd.md 3.5). SALE은 판매로 생기고 VISIT·RECALL은 메모에서 날짜를 정해 만든다 (CAL-10·11). */
public enum FollowUpKind {
    SALE,
    VISIT,
    RECALL
}
