-- 기본 데이터 (ADR-019). 앱이 켜질 때마다 실행되므로 여러 번 실행해도 중복되지 않게 쓴다.
-- 매장 이름은 가상 데이터다 (NFR-07). 계정은 코드로 넣는다 (DevAccountSeeder, B0-3).

-- 매장
INSERT IGNORE INTO store (code, name, active) VALUES ('GN01', '강남점', TRUE);
INSERT IGNORE INTO store (code, name, active) VALUES ('HD01', '홍대점', TRUE);

-- 등급 기준 1번째 버전 (01-requirements.md BR-04의 데모용 초기값)
INSERT IGNORE INTO grade_policy (version, period_months, applies_from, created_by, created_at)
VALUES (1, 12, '2026-06-01', NULL, NOW(6));

INSERT INTO grade_rule (policy_id, kind, min_value, score, grade_code)
SELECT p.id, r.kind, r.min_value, r.score, r.grade_code
FROM grade_policy p
JOIN (
    SELECT 'AMOUNT' AS kind, 0 AS min_value, 0 AS score, NULL AS grade_code
    UNION ALL SELECT 'AMOUNT', 1000000, 10, NULL
    UNION ALL SELECT 'AMOUNT', 2000000, 20, NULL
    UNION ALL SELECT 'AMOUNT', 3000000, 40, NULL
    UNION ALL SELECT 'AMOUNT', 4000000, 60, NULL
    UNION ALL SELECT 'TICKET', 0, 0, NULL
    UNION ALL SELECT 'TICKET', 1, 10, NULL
    UNION ALL SELECT 'TICKET', 2, 20, NULL
    UNION ALL SELECT 'TICKET', 3, 30, NULL
    UNION ALL SELECT 'TICKET', 4, 40, NULL
    UNION ALL SELECT 'GRADE', 0, NULL, 'NORMAL'
    UNION ALL SELECT 'GRADE', 20, NULL, 'D'
    UNION ALL SELECT 'GRADE', 40, NULL, 'C'
    UNION ALL SELECT 'GRADE', 70, NULL, 'B'
    UNION ALL SELECT 'GRADE', 100, NULL, 'A'
) r
WHERE p.version = 1
  AND NOT EXISTS (SELECT 1 FROM grade_rule gr WHERE gr.policy_id = p.id);

-- 등급별 할인율 (A 5% · B 3% · C 2% · D 1% · 일반 0%)
INSERT IGNORE INTO grade_discount (grade_code, rate, updated_by, updated_at) VALUES ('NORMAL', 0, NULL, NOW(6));
INSERT IGNORE INTO grade_discount (grade_code, rate, updated_by, updated_at) VALUES ('D', 1, NULL, NOW(6));
INSERT IGNORE INTO grade_discount (grade_code, rate, updated_by, updated_at) VALUES ('C', 2, NULL, NOW(6));
INSERT IGNORE INTO grade_discount (grade_code, rate, updated_by, updated_at) VALUES ('B', 3, NULL, NOW(6));
INSERT IGNORE INTO grade_discount (grade_code, rate, updated_by, updated_at) VALUES ('A', 5, NULL, NOW(6));

-- 후속 연락 3단계 (BR-14): 2일 감사 인사, 2주 착용감 확인, 2달 신상·재방문 안내(홍보)
INSERT INTO follow_up_rule (name, offset_amount, offset_unit, promotional, sort_order, active, updated_at)
SELECT s.name, s.offset_amount, s.offset_unit, s.promotional, s.sort_order, TRUE, NOW(6)
FROM (
    SELECT '감사 인사' AS name, 2 AS offset_amount, 'DAY' AS offset_unit, FALSE AS promotional, 1 AS sort_order
    UNION ALL SELECT '착용감 확인', 2, 'WEEK', FALSE, 2
    UNION ALL SELECT '신상·재방문 안내', 2, 'MONTH', TRUE, 3
) s
WHERE NOT EXISTS (SELECT 1 FROM follow_up_rule);
