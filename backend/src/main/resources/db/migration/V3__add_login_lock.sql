-- 로그인 5번 연속 실패하면 10분 잠금 (AUTH-01, 총괄 결정 11/20). 성공하면 횟수는 0으로 돌아간다.
ALTER TABLE staff
    ADD COLUMN failed_login_count INT         NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수. 성공하거나 잠기면 0',
    ADD COLUMN locked_until       DATETIME(6) NULL     COMMENT '이 시각까지 로그인 거절. NULL이거나 지났으면 잠기지 않음';
