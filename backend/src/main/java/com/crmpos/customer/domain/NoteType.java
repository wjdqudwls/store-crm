package com.crmpos.customer.domain;

public enum NoteType {
    CONSULT,
    PURCHASE,
    CALL,
    /** 방문 예약 (CAL-10). 날짜로 할 일을 만드는 일은 후속 연락 작업(B4-3)에서 한다. */
    RESERVATION,
    ETC
}
