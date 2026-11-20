package com.crmpos.customer.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 메모 수정 요청: 내용만 고친다 (MEM-03). */
public record NoteUpdateRequest(@NotBlank @Size(max = 1000) String content) {

    @Override
    public String toString() {
        return "NoteUpdateRequest[]";
    }
}
