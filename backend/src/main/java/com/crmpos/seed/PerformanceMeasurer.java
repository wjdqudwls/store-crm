package com.crmpos.seed;

import com.crmpos.common.web.KstTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 성능 측정 (B4-5, NFR-01·02). {@code seed} 프로필에 {@code crm.seed.measure=true}를 주면 서버가 뜬 뒤 더미 데이터로
 * 응답 시간을 재서 마크다운 표로 로그에 남긴다. 실제 HTTP 요청을 순서대로 보내(동시 요청 없음) 잰다.
 * 판매 → 고객 반영 시간은 outbox 이벤트의 {@code published_at - created_at}이다(받는 쪽 저장이 같은 트랜잭션).
 * 정기 산정·엑셀·퇴사 이관(NFR-02a)은 아직 구현되지 않아 재지 않는다.
 */
@Slf4j
@Component
@Profile("seed")
@ConditionalOnProperty(name = "crm.seed.measure", havingValue = "true")
public class PerformanceMeasurer implements ApplicationRunner {

    private static final int WARMUP = 20;
    private static final int SAMPLES = 100;

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Environment env;
    private final String storePassword;

    public PerformanceMeasurer(ObjectMapper mapper, JdbcTemplate jdbc, Environment env,
            @Value("${crm.accounts.store-password}") String storePassword) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.env = env;
        this.storePassword = storePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        new Thread(this::measure, "seed-measure").start();
    }

    private void measure() {
        try {
            // 생성기가 끝나기를 기다린다: 이벤트가 모두 처리된 뒤에 잰다
            waitUntilQuiet();
            SeedApi api = new SeedApi(mapper, env.getRequiredProperty("local.server.port", Integer.class));
            String token = api.login("store-gn01", storePassword);
            long storeId = api.get("/me", token).body().get("storeId").asLong();
            Random random = new Random(42);
            List<Long> heavy = jdbc.queryForList(
                    "SELECT customer_id FROM customer_purchase GROUP BY customer_id ORDER BY COUNT(*) DESC LIMIT 50", Long.class);
            List<String> names = jdbc.queryForList("SELECT DISTINCT SUBSTRING(name, 1, 2) FROM customer LIMIT 200", String.class);
            LocalDate today = LocalDate.now(KstTime.ZONE);

            List<String> rows = new ArrayList<>();
            rows.add(row("고객 검색(이름 앞 2글자, 20건)", () -> {
                String name = names.get(random.nextInt(names.size()));
                return api.get("/customers?name=" + enc(name) + "&size=20", token);
            }));
            rows.add(row("고객 검색(이름 전체 3글자)", () -> {
                String name = jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class,
                        1L + random.nextInt(5000));
                return api.get("/customers?name=" + enc(name), token);
            }));
            rows.add(row("고객 상세", () -> api.get("/customers/" + heavy.get(random.nextInt(heavy.size())), token)));
            rows.add(row("고객 구매이력(많이 산 고객)", () -> api.get(
                    "/customers/" + heavy.get(random.nextInt(heavy.size())) + "/purchases", token)));
            rows.add(row("콜리스트(매장 기본값)", () -> api.get("/call-list", token)));
            rows.add(row("콜리스트(조건 모두 끔)", () -> api.get(
                    "/call-list?minGrade=NORMAL&inactiveDays=0&promoOnly=false", token)));
            rows.add(row("판매 내역(최근 30일)", () -> api.get(
                    "/sales?from=" + today.minusDays(30) + "&to=" + today + "&size=50", token)));
            rows.add(row("판매 내역(6개월 전체)", () -> api.get(
                    "/sales?from=" + today.minusDays(185) + "&to=" + today + "&size=50", token)));
            long skuId = jdbc.queryForObject("SELECT MIN(id) FROM product_sku", Long.class);
            long staffId = api.get("/stores/" + storeId + "/staff", token).body().get(0).get("id").asLong();
            rows.add(row("판매 기록(POST /sales)", () -> {
                Map<String, Object> body = Map.of("sellerStaffId", staffId, "customerId",
                        heavy.get(random.nextInt(heavy.size())), "lines",
                        List.of(Map.of("skuId", skuId, "qty", 1, "lineAmount", 100000)));
                return api.post("/sales", token, body, UUID.randomUUID().toString());
            }, 201));

            // 방금 기록한 판매(WARMUP+SAMPLES건)가 고객 쪽에 반영되기까지 걸린 시간
            waitUntilQuiet();
            List<Long> lagMs = jdbc.queryForList("""
                    SELECT TIMESTAMPDIFF(MICROSECOND, created_at, published_at) / 1000
                    FROM outbox_event WHERE status = 'PUBLISHED' ORDER BY created_at DESC LIMIT ?""", Long.class,
                    WARMUP + SAMPLES);
            Collections.sort(lagMs);
            rows.add(String.format("| 판매 → 고객 반영(%d건) | %d | %d | %d |", lagMs.size(), pick(lagMs, 50),
                    pick(lagMs, 95), lagMs.get(lagMs.size() - 1)));

            StringBuilder out = new StringBuilder("\n| 항목 | p50(ms) | p95(ms) | 최대(ms) |\n| --- | ---: | ---: | ---: |\n");
            rows.forEach(r -> out.append(r).append('\n'));
            out.append(String.format("고객 %d명, 판매 %d건, 영수증 라인 %d, 이벤트 %d개, outbox 간격 %sms%n", count("customer"),
                    count("sale"), count("sale_line"), count("outbox_event"),
                    env.getProperty("crm.outbox.publisher.interval-ms", "1000")));
            log.warn("seed 성능 측정 결과{}", out);
        } catch (RuntimeException e) {
            log.error("seed 성능 측정 실패 type={}", e.getClass().getSimpleName(), e);
        }
    }

    private String row(String label, Supplier<SeedApi.Response> call) {
        return row(label, call, 200);
    }

    private String row(String label, Supplier<SeedApi.Response> call, int expectedStatus) {
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < WARMUP + SAMPLES; i++) {
            long start = System.nanoTime();
            SeedApi.Response res = call.get();
            long micros = (System.nanoTime() - start) / 1000;
            if (res.status() != expectedStatus) {
                throw new IllegalStateException(label + " 응답 status=" + res.status());
            }
            if (i >= WARMUP) {
                times.add(micros);
            }
        }
        Collections.sort(times);
        return String.format("| %s | %.1f | %.1f | %.1f |", label, pick(times, 50) / 1000.0, pick(times, 95) / 1000.0,
                times.get(times.size() - 1) / 1000.0);
    }

    /** 가까운 순위(nearest-rank) 백분위수 */
    static long pick(List<Long> sorted, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private void waitUntilQuiet() {
        for (int i = 0; i < 3600; i++) {
            int customers = count("customer");
            int pending = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING'", Integer.class);
            if (customers > 0 && pending == 0 && !generatorRunning()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean generatorRunning() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> "seed-generator".equals(t.getName()) && t.isAlive());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
