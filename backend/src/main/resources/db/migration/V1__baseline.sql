-- CRM pos 테이블 정의 (02-erd.md 기준, ADR-010)
-- 앱이 켜질 때마다 실행된다. 여러 번 실행해도 안전하도록 CREATE TABLE IF NOT EXISTS로 쓴다.
-- 그래서 이미 만든 표는 다시 만들지 않는다. 표를 바꾼 뒤 로컬에서는 DB를 비우고 다시 띄운다 (docker compose down -v → up -d).
--
-- 옮기는 규칙
-- - id: BIGINT AUTO_INCREMENT PRIMARY KEY
-- - 금액: BIGINT(원), 시각: DATETIME(6), enum: VARCHAR(20) 문자열
-- - 문서에 NULL 가능이라고 적힌 컬럼만 NULL, 나머지는 NOT NULL
-- - FK는 같은 모듈 안에서만 건다. 다른 모듈의 ID는 컬럼만 두고 FK를 걸지 않는다 (ADR-003)
-- - 이름 붙이기: UNIQUE는 uk_테이블_컬럼, 인덱스는 idx_테이블_컬럼, FK는 fk_테이블_참조테이블
--   (한 표가 같은 표를 여러 번 가리키면 fk_테이블_컬럼, 예: fk_sale_origin_sale)

-- ============================================================
-- common: store, staff, staff_notification
-- ============================================================

CREATE TABLE IF NOT EXISTS store (
    id     BIGINT      NOT NULL AUTO_INCREMENT,
    code   VARCHAR(20) NOT NULL COMMENT 'QR에 쓰는 매장 코드 (예: GN01)',
    name   VARCHAR(50) NOT NULL,
    active BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_store_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS staff (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    login_id           VARCHAR(50)  NOT NULL,
    password_hash      VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    name               VARCHAR(50)  NOT NULL,
    role               VARCHAR(20)  NOT NULL COMMENT 'HQ, STORE, MANAGER, SENIOR, JUNIOR, STAFF, KIOSK (ADR-023)',
    manager_slot       VARCHAR(20)  NULL     COMMENT 'MAIN, TEMP. 활성 MANAGER만, 그 밖에는 NULL (BR-15)',
    store_id           BIGINT       NULL     COMMENT '본사 계정은 NULL',
    staff_code         VARCHAR(20)  NULL     COMMENT 'QR 담당 지정용 (예: S017). 키오스크·본사 계정은 NULL',
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    tokens_valid_after DATETIME(6)  NOT NULL COMMENT '이 시각보다 먼저 발급된 토큰은 거절. 계정을 만들 때 그 시각, 비밀번호 변경·모든 기기 로그아웃 때 지금 시각 (ADR-025)',
    PRIMARY KEY (id),
    CONSTRAINT uk_staff_login_id UNIQUE (login_id),
    CONSTRAINT uk_staff_staff_code UNIQUE (staff_code),
    CONSTRAINT uk_staff_store_manager_slot UNIQUE (store_id, manager_slot),
    INDEX idx_staff_store_role_active (store_id, role, active),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 직원 알림함 (CAL-09). 문구는 알림을 만들 때 고객 모듈이 정해 넘긴다 (ADR-029)
CREATE TABLE IF NOT EXISTS staff_notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    staff_id    BIGINT       NOT NULL COMMENT '받는 직원',
    type        VARCHAR(20)  NOT NULL COMMENT 'FOLLOW_UP, NOTE',
    customer_id BIGINT       NULL     COMMENT '고객 모듈의 ID라 FK 없음 (ADR-003)',
    ref_id      BIGINT       NOT NULL COMMENT 'FOLLOW_UP이면 follow_up_task.id, NOTE면 consult_note.id',
    message     VARCHAR(200) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    read_at     DATETIME(6)  NULL     COMMENT 'NULL = 안 읽음',
    PRIMARY KEY (id),
    INDEX idx_staff_notification_staff_read_created (staff_id, read_at, created_at),
    CONSTRAINT fk_staff_notification_staff FOREIGN KEY (staff_id) REFERENCES staff (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- product: product, product_sku
-- ============================================================

CREATE TABLE IF NOT EXISTS product (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_code VARCHAR(20)  NULL     COMMENT '품번 (예: D2110), 선택. NULL은 여러 개여도 UNIQUE에 걸리지 않는다',
    name         VARCHAR(100) NOT NULL COMMENT '품명',
    target       VARCHAR(20)  NULL     COMMENT 'WOMEN, MEN, KIDS, UNISEX, 선택',
    item_type    VARCHAR(20)  NOT NULL COMMENT 'OUTER, TOP, BOTTOM, SHOES, ACC',
    list_price   BIGINT       NOT NULL COMMENT '정가 (원)',
    PRIMARY KEY (id),
    CONSTRAINT uk_product_product_code UNIQUE (product_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_sku (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    product_id BIGINT      NOT NULL,
    color      VARCHAR(30) NOT NULL COMMENT '선택. 색상이 없으면 빈 문자열로 저장해 UNIQUE가 동작하게 한다',
    size       VARCHAR(20) NOT NULL COMMENT '품목별 허용값만 (02-erd.md 3.2)',
    PRIMARY KEY (id),
    CONSTRAINT uk_product_sku_product_color_size UNIQUE (product_id, color, size),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- sales: sale, sale_line, sale_return, sale_return_line, receipt_sequence, store_day_close, outbox_event
-- store_id, staff_id, recorded_by, customer_id, sku_id, wearer_member_id는 다른 모듈의 ID라 FK를 걸지 않는다 (ADR-003)
-- ============================================================

CREATE TABLE IF NOT EXISTS sale (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    store_id        BIGINT      NOT NULL COMMENT '판매 매장 = 로그인한 계정의 소속 매장',
    receipt_no      VARCHAR(30) NOT NULL COMMENT '매장코드-판매일자-순번 (예: GN01-20261015-0012)',
    idempotency_key CHAR(36)    NOT NULL COMMENT 'UUID, 중복 요청 방지 (ADR-006)',
    request_hash    CHAR(64)    NOT NULL COMMENT '요청 본문 SHA-256 (16진수)',
    staff_id        BIGINT      NOT NULL COMMENT '판매 사원 (판매 화면에서 고름)',
    recorded_by     BIGINT      NOT NULL COMMENT '기록한 계정 (보통 매장 계정, 마감 뒤 정정이면 본사)',
    customer_id     BIGINT      NULL     COMMENT 'NULL이면 비회원 판매',
    applied_grade   VARCHAR(20) NULL     COMMENT '판매 당시 등급, 비회원 NULL',
    sold_at         DATETIME(6) NOT NULL,
    total_amount    BIGINT      NOT NULL COMMENT '실제 결제 금액 합계 (ADR-014)',
    total_qty       INT         NOT NULL,
    kind            VARCHAR(20) NOT NULL COMMENT 'NORMAL, EXCHANGE, CORRECTION',
    origin_sale_id  BIGINT      NULL     COMMENT '교환·정정의 원 판매 (바로 앞 판매). 일반 판매는 NULL',
    root_sale_id    BIGINT      NULL     COMMENT '처음 판매. 교환·정정은 원 판매의 값을 물려받고, 일반 판매는 자기 ID라 저장 직후 같은 트랜잭션에서 채운다 (ADR-022)',
    status          VARCHAR(20) NOT NULL COMMENT 'COMPLETED, PARTIAL_RETURNED, RETURNED, VOIDED',
    created_at      DATETIME(6) NOT NULL COMMENT '기록한 시각. 판매 시각(sold_at)과 다를 수 있다',
    PRIMARY KEY (id),
    CONSTRAINT uk_sale_receipt_no UNIQUE (receipt_no),
    CONSTRAINT uk_sale_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_sale_store_sold_at (store_id, sold_at),
    INDEX idx_sale_customer_sold_at (customer_id, sold_at),
    INDEX idx_sale_origin_sale (origin_sale_id),
    CONSTRAINT fk_sale_origin_sale FOREIGN KEY (origin_sale_id) REFERENCES sale (id),
    CONSTRAINT fk_sale_root_sale FOREIGN KEY (root_sale_id) REFERENCES sale (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_line (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    sale_id          BIGINT       NOT NULL,
    sku_id           BIGINT       NULL     COMMENT 'NULL이면 직접 입력 (등록 상품 아님)',
    product_code     VARCHAR(20)  NULL     COMMENT '판매 시점 스냅샷, 선택',
    product_name     VARCHAR(100) NOT NULL COMMENT '스냅샷',
    target           VARCHAR(20)  NULL     COMMENT '스냅샷, 선택',
    item_type        VARCHAR(20)  NOT NULL COMMENT '스냅샷',
    color            VARCHAR(30)  NULL     COMMENT '스냅샷, 선택',
    size             VARCHAR(20)  NOT NULL COMMENT '스냅샷',
    list_price       BIGINT       NULL     COMMENT '정가 스냅샷, 등록 상품일 때만',
    qty              INT          NOT NULL,
    line_amount      BIGINT       NOT NULL COMMENT '직원이 확인한 실제 결제 금액 (ADR-014)',
    returned_qty     INT          NOT NULL DEFAULT 0,
    returned_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '이 줄의 sale_return_line.amount 합계. 마지막 남은 수량의 환불액 계산에 쓴다 (BR-11, ADR-030)',
    wearer_member_id BIGINT       NULL     COMMENT '착용자, 선택 (ADR-009)',
    wearer_label     VARCHAR(30)  NULL     COMMENT '착용자 호칭 스냅샷',
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_line_sale FOREIGN KEY (sale_id) REFERENCES sale (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 반품·교환·기록 취소. 원 판매를 고치지 않고 따로 남긴다 (BR-16, ADR-026)
CREATE TABLE IF NOT EXISTS sale_return (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    sale_id         BIGINT      NOT NULL,
    idempotency_key CHAR(36)    NOT NULL COMMENT 'UUID, 중복 요청 방지 (ADR-006)',
    request_hash    CHAR(64)    NOT NULL COMMENT '요청 본문 SHA-256 (16진수)',
    kind            VARCHAR(20) NOT NULL COMMENT 'RETURN, EXCHANGE, VOID (기록 취소)',
    staff_id        BIGINT      NOT NULL COMMENT '처리한 직원 (고름)',
    recorded_by     BIGINT      NOT NULL COMMENT '기록한 계정',
    returned_at     DATETIME(6) NOT NULL,
    amount          BIGINT      NOT NULL COMMENT '환불 합계 (BR-11)',
    new_sale_id     BIGINT      NULL     COMMENT '짝인 새 판매 (교환으로 받은 판매, 정정 판매). 일반 반품·단순 기록 취소는 NULL',
    PRIMARY KEY (id),
    CONSTRAINT uk_sale_return_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_sale_return_sale FOREIGN KEY (sale_id) REFERENCES sale (id),
    CONSTRAINT fk_sale_return_new_sale FOREIGN KEY (new_sale_id) REFERENCES sale (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_return_line (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    return_id    BIGINT NOT NULL,
    sale_line_id BIGINT NOT NULL,
    qty          INT    NOT NULL,
    amount       BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_return_line_return FOREIGN KEY (return_id) REFERENCES sale_return (id),
    CONSTRAINT fk_sale_return_line_sale_line FOREIGN KEY (sale_line_id) REFERENCES sale_line (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 영수증 순번: (매장, 날짜) 행을 SELECT ... FOR UPDATE로 잠그고 1 올린다
CREATE TABLE IF NOT EXISTS receipt_sequence (
    store_id  BIGINT NOT NULL,
    sale_date DATE   NOT NULL,
    last_no   INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (store_id, sale_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 하루 마감. 그날 판매는 날짜가 지났거나, 마감 행이 있고 reopened_at이 NULL이면 잠긴다 (02-erd.md 3.3)
-- 다시 연 날을 다시 마감하면 같은 행의 closed_*를 새로 쓰고 reopened_*를 비운다
CREATE TABLE IF NOT EXISTS store_day_close (
    store_id      BIGINT      NOT NULL,
    business_date DATE        NOT NULL,
    closed_by     BIGINT      NOT NULL COMMENT '마감한 계정',
    closed_at     DATETIME(6) NOT NULL,
    reopened_by   BIGINT      NULL     COMMENT '다시 연 본사 계정',
    reopened_at   DATETIME(6) NULL     COMMENT 'NULL이 아니면 다시 열린 상태',
    PRIMARY KEY (store_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 판매·반품과 같은 트랜잭션에서 기록하고, 발행기가 고객 모듈에 전달한다 (ADR-002, ADR-007)
CREATE TABLE IF NOT EXISTS outbox_event (
    event_id     CHAR(36)    NOT NULL COMMENT 'UUID',
    event_type   VARCHAR(30) NOT NULL COMMENT 'SALE_COMPLETED, SALE_RETURNED',
    aggregate_id BIGINT      NOT NULL COMMENT '처음 판매 ID = sale.root_sale_id. 교환·정정이 이어져도 한 순서 묶음 (ADR-022)',
    payload      JSON        NOT NULL COMMENT '03-api.md 6장의 이벤트 계약',
    status       VARCHAR(20) NOT NULL COMMENT 'PENDING, PUBLISHED, FAILED',
    attempts     INT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (event_id),
    INDEX idx_outbox_event_status_created_at (status, created_at),
    INDEX idx_outbox_event_aggregate_created_at (aggregate_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- customer: customer, customer_member, customer_purchase, customer_summary,
--           grade_policy, grade_rule, grade_discount, grade_history, consult_note,
--           follow_up_rule, follow_up_task, processed_event
-- home_store_id, staff_id, recorded_by, sale_id, sale_line_id, store_id는 다른 모듈의 ID라 FK를 걸지 않는다 (ADR-003)
-- ============================================================

CREATE TABLE IF NOT EXISTS customer (
    id                        BIGINT          NOT NULL AUTO_INCREMENT COMMENT '고객번호 = 식별 키 (BR-01)',
    name                      VARCHAR(50)     NOT NULL COMMENT '검색용 이름. 외국인은 한글 이름이 없으면 영문 이름 (REG-06)',
    name_en                   VARCHAR(100)    NULL     COMMENT '영문 이름 (외국인 필수)',
    phone_enc                 VARBINARY(255)  NOT NULL COMMENT '전화번호 암호문 (NFR-04)',
    phone_hash                CHAR(64)        NOT NULL COMMENT '정규화한 번호의 HMAC-SHA256 (16진수), 중복 확인',
    phone_last4               CHAR(4)         NOT NULL COMMENT '번호 뒷자리, 목록 표시·검색 좁히기',
    nationality               VARCHAR(20)     NOT NULL COMMENT 'DOMESTIC, FOREIGN',
    birth_year                SMALLINT        NULL,
    birth_md                  CHAR(4)         NULL     COMMENT 'MMDD',
    region_sido               VARCHAR(20)     NULL,
    region_gu                 VARCHAR(20)     NULL,
    address_detail_enc        VARBINARY(512)  NULL     COMMENT '상세주소 암호문 (BR-08)',
    gender                    VARCHAR(20)     NULL     COMMENT 'F, M',
    preferred_item_types      JSON            NULL     COMMENT '복수 선택, 예: ["OUTER","TOP"]',
    size_top                  VARCHAR(20)     NULL,
    size_outer                VARCHAR(20)     NULL     COMMENT '성별을 고르지 않으면 비움 (REG-01)',
    size_bottom               VARCHAR(20)     NULL     COMMENT '성별을 고르지 않으면 비움 (REG-01)',
    size_shoes                VARCHAR(20)     NULL,
    contact_channel           VARCHAR(20)     NULL     COMMENT 'CALL, SMS, KAKAO',
    home_store_id             BIGINT          NULL     COMMENT '가입매장. 본사 계정이 등록하면 NULL',
    staff_id                  BIGINT          NULL     COMMENT '담당 직원. 정할 수 없으면 NULL (REG-02·03)',
    source                    VARCHAR(20)     NOT NULL COMMENT 'KIOSK, QR, STORE (매장 계정에서 직원이 대신 등록)',
    privacy_agreed_at         DATETIME(6)     NOT NULL COMMENT '개인정보 수집·이용 동의 (필수)',
    age14_confirmed_at        DATETIME(6)     NOT NULL COMMENT '만 14세 이상 확인 (필수)',
    marketing_sms_agreed_at   DATETIME(6)     NULL     COMMENT 'NULL = 미동의',
    marketing_kakao_agreed_at DATETIME(6)     NULL     COMMENT 'NULL = 미동의',
    created_at                DATETIME(6)     NOT NULL COMMENT '가입일',
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_phone_hash UNIQUE (phone_hash),
    INDEX idx_customer_name (name),
    INDEX idx_customer_name_phone_last4 (name, phone_last4),
    INDEX idx_customer_staff (staff_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_member (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id BIGINT      NOT NULL COMMENT '대표 고객',
    nickname    VARCHAR(30) NOT NULL COMMENT '호칭, 실명 아님 (예: 첫째 딸)',
    relation    VARCHAR(20) NOT NULL COMMENT 'SPOUSE, CHILD, PARENT, OTHER',
    gender      VARCHAR(20) NULL     COMMENT 'F, M',
    birth_year  SMALLINT    NULL,
    size_top    VARCHAR(20) NULL,
    size_outer  VARCHAR(20) NULL,
    size_bottom VARCHAR(20) NULL,
    size_shoes  VARCHAR(20) NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_customer_member_customer (customer_id),
    INDEX idx_customer_member_relation_customer (relation, customer_id),
    CONSTRAINT fk_customer_member_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 판매 이벤트로 받은 라인 단위 구매 기록 (판매 테이블 사본, ADR-003). 행은 지우지 않는다
CREATE TABLE IF NOT EXISTS customer_purchase (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id       BIGINT      NOT NULL,
    sale_id           BIGINT      NOT NULL,
    sale_line_id      BIGINT      NOT NULL,
    store_id          BIGINT      NOT NULL,
    sold_at           DATETIME(6) NOT NULL,
    purchase_group_id BIGINT      NOT NULL COMMENT '구매 건 = 처음 판매 ID (이벤트의 rootSaleId, ADR-022)',
    counted_at        DATETIME(6) NOT NULL COMMENT '실적에 넣는 시각 = 처음 판매 시각',
    target            VARCHAR(20) NULL     COMMENT '모르면 NULL',
    item_type         VARCHAR(20) NOT NULL,
    qty               INT         NOT NULL,
    amount            BIGINT      NOT NULL,
    returned_qty      INT         NOT NULL DEFAULT 0 COMMENT '나간 수량 (반품·교환·기록 취소)',
    voided_qty        INT         NOT NULL DEFAULT 0 COMMENT 'returned_qty 중 기록 취소분 (BR-16)',
    returned_amount   BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_purchase_sale_line UNIQUE (sale_line_id),
    INDEX idx_customer_purchase_customer_counted_at (customer_id, counted_at),
    INDEX idx_customer_purchase_purchase_group (purchase_group_id),
    INDEX idx_customer_purchase_item_type_customer (item_type, customer_id),
    INDEX idx_customer_purchase_target_customer (target, customer_id),
    CONSTRAINT fk_customer_purchase_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 고객당 1행의 조회용 요약. 고객을 등록하는 같은 트랜잭션에서 만든다. 등급 3컬럼은 02-erd.md 3.4, ADR-004
CREATE TABLE IF NOT EXISTS customer_summary (
    customer_id         BIGINT      NOT NULL,
    season_ticket_count INT         NOT NULL DEFAULT 0 COMMENT '이번 시즌(6월 1일 0시부터) 누적',
    season_qty          INT         NOT NULL DEFAULT 0 COMMENT '이번 시즌 누적',
    season_net_amount   BIGINT      NOT NULL DEFAULT 0 COMMENT '이번 시즌 누적',
    lifetime_amount     BIGINT      NOT NULL DEFAULT 0,
    first_visit_at      DATETIME(6) NULL     COMMENT '구매가 없으면 NULL (BR-09)',
    last_visit_at       DATETIME(6) NULL     COMMENT '구매가 없으면 NULL (BR-09)',
    ticket_score        INT         NOT NULL DEFAULT 0,
    amount_score        INT         NOT NULL DEFAULT 0,
    total_score         INT         NOT NULL DEFAULT 0,
    computed_grade      VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '지금 실적 기준',
    current_grade       VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '지금 적용 중',
    confirmed_grade     VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '마지막 정기 산정 (6월은 임시)',
    policy_version      INT         NOT NULL,
    calculated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (customer_id),
    INDEX idx_customer_summary_current_grade_last_visit (current_grade, last_visit_at),
    CONSTRAINT fk_customer_summary_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 등급 기준. 수정하면 기존 행을 고치지 않고 새 버전을 만든다.
-- 적용 중인 기준은 applies_from이 오늘 이전인 것 중 가장 최근 버전이다 (ADR-027)
CREATE TABLE IF NOT EXISTS grade_policy (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    version       INT         NOT NULL,
    period_months INT         NOT NULL DEFAULT 12 COMMENT '시즌 길이. 시작일은 6월 1일 고정',
    applies_from  DATE        NOT NULL COMMENT '적용 시작일 (다음 6월 1일, 6월에 저장하면 그날)',
    created_by    BIGINT      NULL     COMMENT '만든 본사 계정. 시드 데이터는 NULL',
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_grade_policy_version UNIQUE (version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS grade_rule (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    policy_id  BIGINT      NOT NULL,
    kind       VARCHAR(20) NOT NULL COMMENT 'AMOUNT, TICKET, GRADE',
    min_value  BIGINT      NOT NULL COMMENT '이상(>=) 기준',
    score      INT         NULL     COMMENT 'AMOUNT, TICKET일 때',
    grade_code VARCHAR(20) NULL     COMMENT 'GRADE일 때',
    PRIMARY KEY (id),
    CONSTRAINT fk_grade_rule_policy FOREIGN KEY (policy_id) REFERENCES grade_policy (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 등급별 할인율 (안내용, BR-10). 등급 기준 버전과 따로 두고, 바꾸면 바로 적용한다 (ADR-027)
CREATE TABLE IF NOT EXISTS grade_discount (
    grade_code VARCHAR(20) NOT NULL COMMENT 'NORMAL, D, C, B, A',
    rate       INT         NOT NULL COMMENT '할인율 %',
    updated_by BIGINT      NULL     COMMENT '바꾼 본사 계정. 시드 데이터는 NULL',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (grade_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 등급이 바뀔 때, 그리고 정기 산정·임시 재계산 때는 등급이 그대로여도 고객마다 1행 (GRD-03·06, ADR-021)
CREATE TABLE IF NOT EXISTS grade_history (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT      NOT NULL,
    from_grade     VARCHAR(20) NOT NULL,
    to_grade       VARCHAR(20) NOT NULL,
    reason         VARCHAR(20) NOT NULL COMMENT 'PROMOTION, PERIODIC, PROVISIONAL_RECALC, REVOKE',
    season         VARCHAR(10) NOT NULL COMMENT '점수를 계산한 시즌 (예: 25/26)',
    ticket_count   INT         NOT NULL,
    net_amount     BIGINT      NOT NULL,
    total_score    INT         NOT NULL,
    policy_version INT         NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_grade_history_customer_created_at (customer_id, created_at),
    CONSTRAINT fk_grade_history_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consult_note (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id BIGINT        NOT NULL,
    staff_id    BIGINT        NOT NULL COMMENT '작성 직원 (매장 계정에서 쓰면 고른 직원)',
    type        VARCHAR(20)   NOT NULL COMMENT 'CONSULT, PURCHASE, CALL, ETC',
    call_result VARCHAR(20)   NULL     COMMENT 'type=CALL일 때: COMPLETED, ABSENT, RECALL',
    channel     VARCHAR(20)   NULL     COMMENT 'type=CALL일 때: CALL, SMS, KAKAO',
    recorded_by BIGINT        NOT NULL COMMENT '기록한 계정',
    content     VARCHAR(1000) NOT NULL COMMENT '최대 1,000자 (MEM-01)',
    sale_id     BIGINT        NULL     COMMENT '응대 메모일 때 (SAL-04)',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_consult_note_customer_created_at (customer_id, created_at),
    CONSTRAINT fk_consult_note_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 후속 연락 단계 설정. 브랜드 공통, 본사(HQ)가 바꾼다. 지우지 않고 끄기(active = false)만 한다 (BR-14, CAL-07)
CREATE TABLE IF NOT EXISTS follow_up_rule (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(50) NOT NULL COMMENT '단계 이름 (예: 감사 인사)',
    offset_amount INT         NOT NULL COMMENT '판매일로부터 (예: 2)',
    offset_unit   VARCHAR(20) NOT NULL COMMENT 'DAY, WEEK, MONTH',
    promotional   BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '홍보 연락 단계 (BR-08)',
    sort_order    INT         NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 후속 연락 할 일. 회원 판매 이벤트 처리 때 활성 단계마다 1행 (ADR-016, CAL-06)
-- staff_id: 고객의 담당 직원, 없으면 판매를 기록한 직원 (BR-14)
CREATE TABLE IF NOT EXISTS follow_up_task (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id BIGINT      NOT NULL,
    staff_id    BIGINT      NOT NULL COMMENT '받는 직원',
    sale_id     BIGINT      NOT NULL COMMENT '기준 판매',
    rule_id     BIGINT      NOT NULL,
    rule_name   VARCHAR(50) NOT NULL COMMENT '단계 이름 스냅샷',
    promotional BOOLEAN     NOT NULL COMMENT '홍보 여부 스냅샷',
    due_date    DATE        NOT NULL COMMENT '만기일 = 판매일 + 단계 기간',
    status      VARCHAR(20) NOT NULL COMMENT 'OPEN, DONE, SKIPPED, CANCELLED, REPLACED',
    note_id     BIGINT      NULL     COMMENT '완료한 연락 기록 (consult_note)',
    created_at  DATETIME(6) NOT NULL,
    closed_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_follow_up_task_staff_status_due (staff_id, status, due_date),
    INDEX idx_follow_up_task_customer_status (customer_id, status),
    CONSTRAINT fk_follow_up_task_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_follow_up_task_rule FOREIGN KEY (rule_id) REFERENCES follow_up_rule (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 기본 단계 3개 (BR-14). 표가 비어 있을 때만 넣는다
INSERT INTO follow_up_rule (name, offset_amount, offset_unit, promotional, sort_order, active, updated_at)
SELECT t.name, t.offset_amount, t.offset_unit, t.promotional, t.sort_order, TRUE, NOW(6)
FROM (SELECT '감사 인사' AS name, 2 AS offset_amount, 'DAY' AS offset_unit, FALSE AS promotional, 1 AS sort_order
      UNION ALL SELECT '착용감 확인', 2, 'WEEK', FALSE, 2
      UNION ALL SELECT '신상·재방문 안내', 2, 'MONTH', TRUE, 3) t
WHERE NOT EXISTS (SELECT 1 FROM follow_up_rule);

-- 처리한 이벤트 ID. 같은 이벤트가 다시 오면 건너뛴다 (멱등, NFR-03)
CREATE TABLE IF NOT EXISTS processed_event (
    event_id     CHAR(36)    NOT NULL COMMENT 'UUID',
    event_type   VARCHAR(30) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
