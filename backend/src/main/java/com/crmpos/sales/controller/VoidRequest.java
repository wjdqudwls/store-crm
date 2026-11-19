package com.crmpos.sales.controller;

/** 기록 취소 요청 (03-api.md 4.9). 처리한 직원 staffId는 매장 계정일 때만 보낸다(본사는 계정 자체가 처리자). */
public record VoidRequest(Long staffId) {
}
