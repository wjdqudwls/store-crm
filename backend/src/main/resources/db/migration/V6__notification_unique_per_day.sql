-- 같은 직원·같은 종류·같은 대상(ref)·같은 날의 알림은 하나만 둔다. 배포 중 앱이 2개 뜬 순간 9시 작업이
-- 두 번 돌아도 알림이 겹치지 않게 DB가 막는다. 넣는 쪽은 INSERT IGNORE로 건너뛴다.

-- 이미 겹쳐 쌓인 알림은 가장 오래된 것만 남긴다 (새 UNIQUE가 걸리도록)
DELETE n FROM staff_notification n
JOIN staff_notification o
  ON o.staff_id = n.staff_id AND o.type = n.type AND o.ref_id = n.ref_id
 AND DATE(o.created_at) = DATE(n.created_at) AND o.id < n.id;

-- 날짜는 created_at(한국 시간으로 저장)에서 DB가 계산한다. 코드는 건드리지 않는다.
ALTER TABLE staff_notification
    ADD COLUMN created_date DATE GENERATED ALWAYS AS (DATE(created_at)) STORED COMMENT '알림이 쌓인 날, created_at에서 계산',
    ADD CONSTRAINT uk_staff_notification_per_day UNIQUE (staff_id, type, ref_id, created_date);
