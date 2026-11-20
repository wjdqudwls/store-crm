-- 메모에서 만드는 수동 할 일(방문 예약 VISIT, 재연락 RECALL)을 담는다 (02-erd.md 3.5, CAL-10·11, ADR-035).
-- 판매로 만드는 할 일(SALE)만 기준 판매·단계가 있으므로 두 칸은 비울 수 있게 바꾸고, 종류와 만든 메모를 더한다.
ALTER TABLE follow_up_task
    MODIFY COLUMN sale_id BIGINT NULL COMMENT '기준 판매 (SALE만, 수동 할 일은 NULL)',
    MODIFY COLUMN rule_id BIGINT NULL COMMENT '단계 (SALE만)',
    ADD COLUMN kind           VARCHAR(20) NOT NULL DEFAULT 'SALE' COMMENT 'SALE, VISIT, RECALL' AFTER staff_id,
    ADD COLUMN source_note_id BIGINT      NULL COMMENT '만든 메모 (VISIT·RECALL)' AFTER rule_id,
    ADD INDEX idx_follow_up_task_source_note (source_note_id),
    ADD INDEX idx_follow_up_task_note (note_id);
