-- CRM pos 테이블 정의 (02-erd.md 기준, ADR-010)
-- 앱이 켜질 때마다 실행된다. 여러 번 실행해도 안전하도록 CREATE TABLE IF NOT EXISTS로 쓴다.
--
-- 옮기는 규칙
-- - id: BIGINT AUTO_INCREMENT PRIMARY KEY
-- - 금액: BIGINT(원), 시각: DATETIME(6), enum: VARCHAR(20) 문자열
-- - 문서에 NULL 가능이라고 적힌 컬럼만 NULL, 나머지는 NOT NULL
-- - FK는 같은 모듈 안에서만 건다. 다른 모듈의 ID는 컬럼만 두고 FK를 걸지 않는다 (ADR-003)
-- - 이름 붙이기: UNIQUE는 uk_테이블_컬럼, 인덱스는 idx_테이블_컬럼, FK는 fk_테이블_참조테이블

-- ============================================================
-- common: store, staff
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
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    name          VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL COMMENT 'STAFF, MANAGER, KIOSK',
    store_id      BIGINT       NULL     COMMENT '본사 계정은 NULL',
    staff_code    VARCHAR(20)  NULL     COMMENT 'QR 담당 지정용 (예: S017). 키오스크·본사 계정은 NULL',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_staff_login_id UNIQUE (login_id),
    CONSTRAINT uk_staff_staff_code UNIQUE (staff_code),
    CONSTRAINT fk_staff_store FOREIGN KEY (store_id) REFERENCES store (id)
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
-- sales: sale, sale_line, sale_return, sale_return_line, receipt_sequence, outbox_event
-- store_id, staff_id, customer_id, sku_id, wearer_member_id는 다른 모듈의 ID라 FK를 걸지 않는다 (ADR-003)
-- ============================================================

CREATE TABLE IF NOT EXISTS sale (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    store_id        BIGINT      NOT NULL COMMENT '판매 매장 = 로그인한 계정의 소속 매장',
    receipt_no      VARCHAR(30) NOT NULL COMMENT '매장코드-판매일자-순번 (예: GN01-20261015-0012)',
    idempotency_key CHAR(36)    NOT NULL COMMENT 'UUID, 중복 요청 방지 (ADR-006)',
    request_hash    CHAR(64)    NOT NULL COMMENT '요청 본문 SHA-256 (16진수)',
    staff_id        BIGINT      NOT NULL,
    customer_id     BIGINT      NULL     COMMENT 'NULL이면 비회원 판매',
    applied_grade   VARCHAR(20) NULL     COMMENT '판매 당시 등급, 비회원 NULL',
    sold_at         DATETIME(6) NOT NULL,
    total_amount    BIGINT      NOT NULL COMMENT '실제 결제 금액 합계 (ADR-014)',
    total_qty       INT         NOT NULL,
    status          VARCHAR(20) NOT NULL COMMENT 'COMPLETED, PARTIAL_RETURNED, RETURNED',
    PRIMARY KEY (id),
    CONSTRAINT uk_sale_receipt_no UNIQUE (receipt_no),
    CONSTRAINT uk_sale_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_sale_store_sold_at (store_id, sold_at),
    INDEX idx_sale_customer_sold_at (customer_id, sold_at)
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
    wearer_member_id BIGINT       NULL     COMMENT '착용자, 선택 (ADR-009)',
    wearer_label     VARCHAR(30)  NULL     COMMENT '착용자 호칭 스냅샷',
    PRIMARY KEY (id),
    CONSTRAINT fk_sale_line_sale FOREIGN KEY (sale_id) REFERENCES sale (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_return (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    sale_id         BIGINT      NOT NULL,
    idempotency_key CHAR(36)    NOT NULL COMMENT 'UUID, 중복 요청 방지 (ADR-006)',
    request_hash    CHAR(64)    NOT NULL COMMENT '요청 본문 SHA-256 (16진수)',
    staff_id        BIGINT      NOT NULL,
    returned_at     DATETIME(6) NOT NULL,
    amount          BIGINT      NOT NULL COMMENT '환불 합계 (BR-11)',
    PRIMARY KEY (id),
    CONSTRAINT uk_sale_return_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_sale_return_sale FOREIGN KEY (sale_id) REFERENCES sale (id)
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

-- 판매·반품과 같은 트랜잭션에서 기록하고, 발행기가 고객 모듈에 전달한다 (ADR-002, ADR-007)
CREATE TABLE IF NOT EXISTS outbox_event (
    event_id     CHAR(36)    NOT NULL COMMENT 'UUID',
    event_type   VARCHAR(30) NOT NULL COMMENT 'SALE_COMPLETED, SALE_RETURNED',
    aggregate_id BIGINT      NOT NULL COMMENT 'sale.id',
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
--           grade_policy, grade_rule, grade_history, consult_note,
--           follow_up_rule, follow_up_task, processed_event
-- home_store_id, staff_id, sale_id, sale_line_id, store_id는 다른 모듈의 ID라 FK를 걸지 않는다 (ADR-003)
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
    source                    VARCHAR(20)     NOT NULL COMMENT 'KIOSK, QR, STAFF',
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

-- 판매 이벤트로 받은 라인 단위 구매 기록 (판매 테이블 사본, ADR-003)
CREATE TABLE IF NOT EXISTS customer_purchase (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id     BIGINT      NOT NULL,
    sale_id         BIGINT      NOT NULL,
    sale_line_id    BIGINT      NOT NULL,
    store_id        BIGINT      NOT NULL,
    sold_at         DATETIME(6) NOT NULL,
    target          VARCHAR(20) NULL     COMMENT '모르면 NULL',
    item_type       VARCHAR(20) NOT NULL,
    qty             INT         NOT NULL,
    amount          BIGINT      NOT NULL,
    returned_qty    INT         NOT NULL DEFAULT 0,
    returned_amount BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_purchase_sale_line UNIQUE (sale_line_id),
    INDEX idx_customer_purchase_customer_sold_at (customer_id, sold_at),
    INDEX idx_customer_purchase_item_type_customer (item_type, customer_id),
    INDEX idx_customer_purchase_target_customer (target, customer_id),
    CONSTRAINT fk_customer_purchase_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 고객당 1행의 조회용 요약. 등급 3컬럼은 02-erd.md 3.4, ADR-004
CREATE TABLE IF NOT EXISTS customer_summary (
    customer_id      BIGINT      NOT NULL,
    ticket_count_12m INT         NOT NULL DEFAULT 0,
    qty_12m          INT         NOT NULL DEFAULT 0,
    net_amount_12m   BIGINT      NOT NULL DEFAULT 0,
    lifetime_amount  BIGINT      NOT NULL DEFAULT 0,
    first_visit_at   DATETIME(6) NULL     COMMENT '구매가 없으면 NULL (BR-09)',
    last_visit_at    DATETIME(6) NULL     COMMENT '구매가 없으면 NULL (BR-09)',
    ticket_score     INT         NOT NULL DEFAULT 0,
    amount_score     INT         NOT NULL DEFAULT 0,
    total_score      INT         NOT NULL DEFAULT 0,
    computed_grade   VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '지금 실적 기준',
    current_grade    VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '지금 적용 중',
    confirmed_grade  VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '마지막 정기 산정',
    policy_version   INT         NOT NULL,
    calculated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (customer_id),
    INDEX idx_customer_summary_current_grade_last_visit (current_grade, last_visit_at),
    CONSTRAINT fk_customer_summary_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 등급 기준. 수정하면 기존 행을 고치지 않고 새 버전을 만든다. 활성 기준 1개는 코드에서 지킨다
CREATE TABLE IF NOT EXISTS grade_policy (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    version             INT         NOT NULL,
    period_months       INT         NOT NULL DEFAULT 12,
    active              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by          BIGINT      NULL     COMMENT '만든 직원. 시드 데이터는 NULL',
    created_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_grade_policy_version UNIQUE (version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS grade_rule (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    policy_id     BIGINT      NOT NULL,
    kind          VARCHAR(20) NOT NULL COMMENT 'AMOUNT, TICKET, GRADE',
    min_value     BIGINT      NOT NULL COMMENT '이상(>=) 기준',
    score         INT         NULL     COMMENT 'AMOUNT, TICKET일 때',
    grade_code    VARCHAR(20) NULL     COMMENT 'GRADE일 때',
    discount_rate INT         NULL     COMMENT 'GRADE일 때 등급 할인율 %',
    PRIMARY KEY (id),
    CONSTRAINT fk_grade_rule_policy FOREIGN KEY (policy_id) REFERENCES grade_policy (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS grade_history (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    customer_id    BIGINT      NOT NULL,
    from_grade     VARCHAR(20) NOT NULL,
    to_grade       VARCHAR(20) NOT NULL,
    reason         VARCHAR(20) NOT NULL COMMENT 'PROMOTION, PERIODIC, RETURN_REVOKE, POLICY_CHANGE',
    policy_version INT         NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_grade_history_customer_created_at (customer_id, created_at),
    CONSTRAINT fk_grade_history_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS consult_note (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id BIGINT        NOT NULL,
    staff_id    BIGINT        NOT NULL COMMENT '작성자',
    type        VARCHAR(20)   NOT NULL COMMENT 'CONSULT, PURCHASE, CALL, ETC',
    call_result VARCHAR(20)   NULL     COMMENT 'type=CALL일 때: COMPLETED, ABSENT, RECALL',
    content     VARCHAR(1000) NOT NULL COMMENT '최대 1,000자 (MEM-01)',
    sale_id     BIGINT        NULL     COMMENT '응대 메모일 때 (SAL-04)',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_consult_note_customer_created_at (customer_id, created_at),
    CONSTRAINT fk_consult_note_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 후속 연락 단계 설정. 브랜드 공통, MANAGER가 바꾼다 (BR-14, CAL-07)
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
