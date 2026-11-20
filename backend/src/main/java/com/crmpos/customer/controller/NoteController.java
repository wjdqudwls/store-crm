package com.crmpos.customer.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 상담 메모 (MEM-01~03, CAL-02, 03-api.md 3.3). 본문 검사는 서비스가 한 번에 모아 400으로 준다. */
@Tag(name = "상담 메모")
@RestController
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @Operation(summary = "메모 목록", description = "고객의 메모를 최신순으로. 로그인한 모든 계정(KIOSK 제외). editable은 지금 계정이 고치고 지울 수 있는지. 없는 고객은 CUSTOMER_NOT_FOUND 404.")
    @GetMapping("/customers/{customerId}/notes")
    public PageResponse<NoteItem> list(CurrentAccount account, @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return noteService.list(customerId, page, size, account);
    }

    @Operation(summary = "메모 작성", description = "매장 계정(staffId=작성 직원 필수)과 직원 개인 계정(본인)이 쓴다. 본사는 ACCESS_DENIED 403. 연락(CALL)은 callResult·channel 필수. 형식 오류는 fieldErrors로 모아 VALIDATION_FAILED 400, 없는 고객은 404. 성공하면 201.")
    @PostMapping("/customers/{customerId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteItem create(CurrentAccount account, @PathVariable Long customerId,
            @RequestBody NoteCreateRequest request) {
        return noteService.create(customerId, request, account);
    }

    @Operation(summary = "메모 수정", description = "내용만 고친다. 작성 직원 본인(개인 계정)·그 매장의 매장 계정·본사. 없으면 NOTE_NOT_FOUND 404, 권한이 없으면 ACCESS_DENIED 403, 입력 오류는 400 (이 순서).")
    @PatchMapping("/notes/{noteId}")
    public NoteItem update(CurrentAccount account, @PathVariable Long noteId, @RequestBody NoteUpdateRequest request) {
        return noteService.update(noteId, request, account);
    }

    @Operation(summary = "메모 삭제", description = "수정과 같은 권한. 성공하면 204.")
    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentAccount account, @PathVariable Long noteId) {
        noteService.delete(noteId, account);
    }
}
