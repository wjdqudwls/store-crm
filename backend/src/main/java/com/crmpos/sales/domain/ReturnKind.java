package com.crmpos.sales.domain;

/** 반품 행의 종류 (02-erd.md 3.3). */
public enum ReturnKind {
    /** 반품. */
    RETURN,
    /** 교환으로 돌려받은 라인. */
    EXCHANGE,
    /** 기록 취소: 남은 모든 라인 (BR-16). */
    VOID
}
