-- 직원 알림 설정과 웹 푸시 구독 (02-erd.md 3.1, CAL-08·12, ADR-035).
-- staff.notify_timing: 방문 예약·재연락 알림 시점 (기본 전날). push_subscription: 직원 폰·PC 브라우저의 웹 푸시 구독
ALTER TABLE staff
    ADD COLUMN notify_timing VARCHAR(20) NOT NULL DEFAULT 'DAY_BEFORE' COMMENT 'NONE, DAY_BEFORE, SAME_DAY, BOTH (CAL-12)';

CREATE TABLE push_subscription (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    staff_id   BIGINT       NOT NULL COMMENT '받는 직원',
    endpoint   VARCHAR(500) NOT NULL COMMENT '웹 푸시 주소',
    p256dh     VARCHAR(255) NOT NULL COMMENT '브라우저 공개키',
    auth       VARCHAR(255) NOT NULL COMMENT '인증 비밀',
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint),
    INDEX idx_push_subscription_staff (staff_id),
    CONSTRAINT fk_push_subscription_staff FOREIGN KEY (staff_id) REFERENCES staff (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
