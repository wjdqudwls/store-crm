package com.crmpos.customer.controller;

import com.crmpos.customer.domain.CallResult;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 메모 작성 요청 (03-api.md 3.3, MEM-01, CAL-02). 매장 계정은 작성 직원 {@code staffId}가 필수이고, 직원 개인 계정은
 * 본인이 작성 직원이라 {@code staffId}를 보내도 무시한다. 연락(CALL)은 결과와 채널이 필수다.
 * 메모 내용은 고객 정보라 toString에 담지 않는다 (NFR-04b).
 */
public record NoteCreateRequest(
        @NotNull NoteType type,
        @NotBlank @Size(max = 1000) String content,
        Long staffId,
        CallResult callResult,
        ContactChannel channel) {

    @Override
    public String toString() {
        return "NoteCreateRequest[type=" + type + "]";
    }
}
