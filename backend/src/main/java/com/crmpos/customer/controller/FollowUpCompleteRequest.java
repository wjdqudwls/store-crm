package com.crmpos.customer.controller;

import com.crmpos.customer.domain.CallResult;
import com.crmpos.customer.domain.ContactChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 할 일을 연락 결과로 완료하는 요청 (03-api.md 3.3 POST /follow-ups/{id}/complete). 본문은 연락 메모와 같다.
 * 매장 계정은 연락한 직원 staffId가 필수이고 직원 개인 계정은 보내지 않는다. 내용은 toString에 담지 않는다.
 */
public record FollowUpCompleteRequest(Long staffId, CallResult callResult, ContactChannel channel,
        @NotBlank @Size(max = 1000) String content) {

    @Override
    public String toString() {
        return "FollowUpCompleteRequest[]";
    }
}
