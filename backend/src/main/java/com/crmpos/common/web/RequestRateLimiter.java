package com.crmpos.common.web;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 키(IP)별로 정해진 시간 동안 받을 수 있는 요청 수를 세는 제한기 (NFR-04a, ADR-019). 서버가 1대라 메모리에서 직접 센다
 * (고정 창: 첫 요청부터 창이 시작하고, 창이 끝나면 다시 센다). 창이 지난 항목은 가끔 정리해 메모리가 무한히 늘지 않게 한다.
 * 키는 IP 주소뿐이고 개인정보는 담지 않는다.
 */
public class RequestRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long startMs, int count) {
    }

    private final int limit;
    private final long windowMs;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RequestRateLimiter(int limit, long windowMs, LongSupplier clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /** 요청 1개를 센다. 한도 안이면 true, 넘으면 false(그 요청은 세지 않는다). */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        if (windows.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.startMs() >= windowMs) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (w.count() < limit) {
                allowed[0] = true;
                return new Window(w.startMs(), w.count() + 1);
            }
            return w;
        });
        return allowed[0];
    }

    /** 모든 기록을 지운다 (테스트용). */
    public void reset() {
        windows.clear();
    }

    private void cleanup(long now) {
        for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext();) {
            if (now - it.next().getValue().startMs() >= windowMs) {
                it.remove();
            }
        }
    }
}
