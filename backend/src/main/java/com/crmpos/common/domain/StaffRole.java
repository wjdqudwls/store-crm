package com.crmpos.common.domain;

/** 계정 역할 (01-requirements.md 2장, ADR-023). */
public enum StaffRole {
    /** 본사. store_id가 없다. */
    HQ,
    /** 매장 계정. 판매는 이 계정으로만 기록한다. */
    STORE,
    /** 직원 직급 (개인 계정). */
    MANAGER,
    SENIOR,
    JUNIOR,
    STAFF,
    /** 매장 iPad. 고객 등록만 한다. */
    KIOSK
}
