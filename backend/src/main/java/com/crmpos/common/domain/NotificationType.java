package com.crmpos.common.domain;

/** 알림 종류 (CAL-09). */
public enum NotificationType {
    /** 후속 연락 할 일. ref_id = follow_up_task.id */
    FOLLOW_UP,
    /** 메모. ref_id = consult_note.id */
    NOTE
}
