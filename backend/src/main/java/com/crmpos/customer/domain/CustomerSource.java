package com.crmpos.customer.domain;

/** 고객이 어디서 등록됐나. */
public enum CustomerSource {
    KIOSK,
    QR,
    /** 매장 계정에서 직원이 대신 등록. */
    STORE
}
