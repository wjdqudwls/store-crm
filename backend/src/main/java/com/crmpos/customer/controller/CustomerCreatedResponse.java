package com.crmpos.customer.controller;

/** 등록 응답 (03-api.md 3.2): 고객번호, 이름, 마스킹한 번호. */
public record CustomerCreatedResponse(Long id, String name, String phoneMasked) {

    @Override
    public String toString() {
        return "CustomerCreatedResponse[id=" + id + "]";
    }
}
