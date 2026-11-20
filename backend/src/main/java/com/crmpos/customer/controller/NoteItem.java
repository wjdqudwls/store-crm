package com.crmpos.customer.controller;

import com.crmpos.common.web.KstTime;
import com.crmpos.customer.domain.CallResult;
import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.NoteType;
import java.time.OffsetDateTime;

/**
 * 메모 항목 (03-api.md 4.12 "메모 항목"). {@code editable}은 지금 로그인한 계정이 고치고 지울 수 있는지다.
 * {@code followUpId}는 이 연락으로 완료한 할 일, {@code reminderDate}는 이 메모로 만든 방문·재연락 날짜다(없으면 null). 내용은 toString에 담지 않는다.
 */
public record NoteItem(Long id, NoteType type, String content, CallResult callResult, ContactChannel channel,
        Long staffId, String staffName, Long saleId, Long followUpId, java.time.LocalDate reminderDate,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, boolean editable) {

    public static NoteItem from(ConsultNote note, String staffName, boolean editable, java.time.LocalDate reminderDate,
            Long followUpId) {
        // 고친 적이 없으면 updatedAt은 null이다
        OffsetDateTime updated = note.getUpdatedAt().equals(note.getCreatedAt()) ? null
                : KstTime.toOffset(note.getUpdatedAt());
        return new NoteItem(note.getId(), note.getType(), note.getContent(), note.getCallResult(), note.getChannel(),
                note.getStaffId(), staffName, note.getSaleId(), followUpId, reminderDate, KstTime.toOffset(note.getCreatedAt()),
                updated, editable);
    }

    @Override
    public String toString() {
        return "NoteItem[id=" + id + "]";
    }
}
