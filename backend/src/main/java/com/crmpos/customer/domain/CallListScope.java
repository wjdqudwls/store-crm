package com.crmpos.customer.domain;

/** 콜리스트 범위 (03-api.md 3.3, BR-09). */
public enum CallListScope {
    /** 내 담당 고객 (staffId 지정이 없으면 로그인한 직원). */
    MINE,
    /** 이 매장 고객 전체: 이 매장에서 가입했거나 산 적 있는 고객 + 매장 계정이 맡은 고객. */
    STORE
}
