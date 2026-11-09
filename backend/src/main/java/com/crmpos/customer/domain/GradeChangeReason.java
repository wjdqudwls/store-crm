package com.crmpos.customer.domain;

/** 등급 이력의 사유 4종 (02-erd.md 3.4, ADR-021). */
public enum GradeChangeReason {
    /** 즉시 승급. */
    PROMOTION,
    /** 정기 산정 (6월 1일). */
    PERIODIC,
    /** 6월 임시 등급 재계산. */
    PROVISIONAL_RECALC,
    /** 승급 취소 (반품·교환·기록 취소). */
    REVOKE
}
