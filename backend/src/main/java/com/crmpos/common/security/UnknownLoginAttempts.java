package com.crmpos.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 없는 로그인 ID의 실패도 같은 규칙(5번 연속 → 10분 잠금)으로 세어 서버 메모리에 둔다. 실제 계정과 응답이 같아져서
 * 잠금 응답으로 아이디가 있는지 없는지 알 수 없다. 재시작하면 사라지는 값이라 DB에 두지 않는다 (서버 1대).
 */
@Component
public class UnknownLoginAttempts {

    static final int LIMIT = 5;
    static final Duration LOCK = Duration.ofMinutes(10);
    /** 공격으로 메모리가 차지 않게 상한을 둔다. 넘으면 지난 잠금·기록부터 지운다. */
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(int failures, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public boolean isLocked(String loginId, Instant now) {
        Entry entry = entries.get(loginId);
        return entry != null && entry.lockedUntil() != null && entry.lockedUntil().isAfter(now);
    }

    /** 실패를 하나 더한다. 5번째면 잠그고 횟수를 0으로 되돌린다. */
    public void recordFailure(String loginId, Instant now) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.entrySet().removeIf(e -> e.getValue().lockedUntil() == null
                    || !e.getValue().lockedUntil().isAfter(now));
        }
        entries.compute(loginId, (key, old) -> {
            int failures = (old == null ? 0 : old.failures()) + 1;
            return failures >= LIMIT ? new Entry(0, now.plus(LOCK)) : new Entry(failures, old == null ? null : old.lockedUntil());
        });
    }
}
