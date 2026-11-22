package com.crmpos.seed;

import com.crmpos.common.web.KstTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 더미 판매 생성기 (ING-01, B4-4). {@code seed} 프로필에서만 켜진다. 서버가 뜬 뒤 자기 API를 불러 상품·고객·판매를 만든다:
 * 실제 입력 검사와 outbox 이벤트가 그대로 돌아서 이벤트 처리 검증도 겸한다. 이미 있는 것은 다시 만들지 않는다
 * (전화번호·품번 중복은 건너뛰고, 판매는 목표 수만큼 있으면 건너뛴다).
 *
 * <p>가상 데이터만 쓴다 (NFR-07): 이름은 조합으로 만든 가상 이름이고 전화번호는 {@code 010-0000-0000}~ 처럼 실제로
 * 쓰이지 않는 번호다. 정기 산정(B3-4)은 아직 없어 건너뛴다.
 */
@Slf4j
@Component
@Profile("seed")
public class DummyDataGenerator implements ApplicationRunner {

    private static final String[] FAMILY = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권"};
    private static final String[] GIVEN = {"가온", "나래", "다온", "라온", "마루", "바다", "사랑", "아름", "하늘", "도윤", "서준",
            "지우", "하준", "예준", "수아", "서연", "민서", "하은", "지호", "유나", "채원", "시우", "은우", "다인", "해든"};
    private static final String[] COLORS = {"블랙", "화이트", "네이비", "베이지", "그레이"};
    /** {품목, 대상, 이름 앞, 정가} */
    static final Object[][] PRODUCT_KINDS = {
            {"OUTER", "WOMEN", "울 코트", 390000L}, {"OUTER", "MEN", "패딩 점퍼", 450000L},
            {"TOP", "WOMEN", "니트", 98000L}, {"TOP", "MEN", "셔츠", 79000L}, {"TOP", "UNISEX", "맨투맨", 69000L},
            {"BOTTOM", "WOMEN", "슬랙스", 89000L}, {"BOTTOM", "MEN", "데님", 99000L},
            {"SHOES", "UNISEX", "스니커즈", 129000L}, {"ACC", "UNISEX", "머플러", 49000L}};

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Environment env;
    private final int customerCount;
    private final int saleCount;
    private final int months;
    private final int threads;
    private final String storePassword;

    public DummyDataGenerator(ObjectMapper mapper, JdbcTemplate jdbc, Environment env,
            @Value("${crm.seed.customers:5000}") int customerCount, @Value("${crm.seed.sales:20000}") int saleCount,
            @Value("${crm.seed.months:6}") int months, @Value("${crm.seed.threads:8}") int threads,
            @Value("${crm.accounts.store-password}") String storePassword) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.env = env;
        this.customerCount = customerCount;
        this.saleCount = saleCount;
        this.months = months;
        this.threads = threads;
        this.storePassword = storePassword;
    }

    private record Store(long id, String token, List<Long> staffIds) {
    }

    private record Sku(long id, long price) {
    }

    @Override
    public void run(ApplicationArguments args) {
        // 서버가 이미 떠 있으므로 시작을 막지 않게 다른 스레드에서 돈다
        Thread thread = new Thread(this::generate, "seed-generator");
        thread.start();
    }

    private void generate() {
        try {
            long started = System.currentTimeMillis();
            int port = env.getRequiredProperty("local.server.port", Integer.class);
            SeedApi api = new SeedApi(mapper, port);
            List<Store> stores = List.of(storeOf(api, "store-gn01"), storeOf(api, "store-hd01"));
            List<Sku> skus = createProducts(api, stores.get(0).token());
            List<List<Long>> customersByStore = createCustomers(api, stores);
            createSales(api, stores, skus, customersByStore);
            waitForEvents();
            log.warn("seed 생성 끝 elapsedSec={} customers={} sales={} (정기 산정 B3-4는 아직 없어 건너뜀)",
                    (System.currentTimeMillis() - started) / 1000, count("customer"), count("sale"));
        } catch (RuntimeException e) {
            log.error("seed 생성 실패 type={}", e.getClass().getSimpleName(), e);
        }
    }

    private Store storeOf(SeedApi api, String loginId) {
        String token = loginWithRetry(api, loginId);
        JsonNode me = api.get("/me", token).body();
        long storeId = me.get("storeId").asLong();
        List<Long> staffIds = new ArrayList<>();
        for (JsonNode staff : api.get("/stores/" + storeId + "/staff", token).body()) {
            if (!staff.get("staffCode").isNull()) {
                staffIds.add(staff.get("id").asLong());
            }
        }
        if (staffIds.isEmpty()) {
            throw new IllegalStateException("판매 사원이 없다 storeId=" + storeId);
        }
        return new Store(storeId, token, staffIds);
    }

    /** 기본 계정 시더가 아직 도는 중일 수 있어 잠깐 기다린다. */
    private String loginWithRetry(SeedApi api, String loginId) {
        for (int i = 0; ; i++) {
            try {
                return api.login(loginId, storePassword);
            } catch (IllegalStateException e) {
                if (i >= 20) {
                    throw e;
                }
                sleep(500);
            }
        }
    }

    private List<Sku> createProducts(SeedApi api, String token) {
        List<Sku> skus = new ArrayList<>();
        int number = 0;
        for (int round = 0; round < 5; round++) {
            for (Object[] kind : PRODUCT_KINDS) {
                number++;
                String itemType = (String) kind[0];
                String target = (String) kind[1];
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("productCode", String.format("SEED-%03d", number));
                body.put("name", kind[2] + " " + number);
                body.put("itemType", itemType);
                body.put("target", target);
                body.put("listPrice", (Long) kind[3] + round * 10000L);
                body.put("colors", List.of(COLORS[round % COLORS.length], COLORS[(round + 1) % COLORS.length]));
                body.put("sizes", sizesFor(itemType, target));
                SeedApi.Response res = api.post("/products", token, body, null);
                if (res.status() == 201) {
                    for (JsonNode sku : res.body().get("skus")) {
                        skus.add(new Sku(sku.get("skuId").asLong(), (Long) kind[3] + round * 10000L));
                    }
                } else if (res.status() == 409) {
                    // 이미 만든 상품: 상품 검색으로 SKU를 다시 읽는다
                    JsonNode found = api.get("/products?keyword=" + body.get("productCode"), token).body().get("content");
                    for (JsonNode product : found) {
                        for (JsonNode sku : product.get("skus")) {
                            skus.add(new Sku(sku.get("skuId").asLong(), product.get("listPrice").asLong()));
                        }
                    }
                } else {
                    throw new IllegalStateException("상품 등록 실패 status=" + res.status());
                }
            }
        }
        log.warn("seed 상품 준비 skus={}", skus.size());
        return skus;
    }

    static List<String> sizesFor(String itemType, String target) {
        return switch (itemType) {
            case "OUTER" -> "WOMEN".equals(target) ? List.of("90", "95", "100") : List.of("100", "105", "110");
            case "TOP" -> List.of("S", "M", "L");
            case "BOTTOM" -> "WOMEN".equals(target) ? List.of("26", "28", "30") : List.of("30", "32", "34");
            case "SHOES" -> List.of("250", "260", "270");
            default -> List.of("FREE");
        };
    }

    /** 전화번호 010-0000-0000부터 차례로, 짝수 번호는 첫 매장 홀수 번호는 둘째 매장 고객이다. 매장별 고객 ID 목록을 돌려준다. */
    private List<List<Long>> createCustomers(SeedApi api, List<Store> stores) {
        List<List<Long>> byStore = List.of(new ArrayList<>(), new ArrayList<>());
        List<Map<String, Object>> bodies = new ArrayList<>();
        Random random = new Random(7);
        for (int i = 0; i < customerCount; i++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", FAMILY[random.nextInt(FAMILY.length)] + GIVEN[random.nextInt(GIVEN.length)]);
            body.put("phone", String.format("010-0000-%04d", i));
            body.put("agreements", Map.of("privacy", true, "age14", true, "marketingSms", i % 3 != 0,
                    "marketingKakao", i % 2 == 0));
            body.put("gender", i % 2 == 0 ? "F" : "M");
            bodies.add(body);
        }
        AtomicInteger created = new AtomicInteger();
        runParallel(customerCount, i -> {
            int storeIndex = i % 2;
            SeedApi.Response res = api.post("/customers", stores.get(storeIndex).token(), bodies.get(i), null);
            if (res.status() == 201) {
                created.incrementAndGet();
                synchronized (byStore.get(storeIndex)) {
                    byStore.get(storeIndex).add(res.body().get("id").asLong());
                }
            } else if (res.status() != 409) {
                throw new IllegalStateException("고객 등록 실패 status=" + res.status());
            }
        });
        // 이미 있던 고객도 판매에 쓰도록 DB에서 다시 모은다 (재실행 때)
        for (int s = 0; s < 2; s++) {
            byStore.get(s).clear();
            byStore.get(s).addAll(jdbc.queryForList("SELECT id FROM customer WHERE home_store_id = ? ORDER BY id",
                    Long.class, stores.get(s).id()));
        }
        log.warn("seed 고객 준비 newlyCreated={} total={}", created.get(), count("customer"));
        return byStore;
    }

    private void createSales(SeedApi api, List<Store> stores, List<Sku> skus, List<List<Long>> customersByStore) {
        int existing = count("sale");
        if (existing >= saleCount) {
            log.warn("seed 판매는 이미 있어 건너뜀 sales={}", existing);
            return;
        }
        int todo = saleCount - existing;
        LocalDate today = LocalDate.now(KstTime.ZONE);
        int days = months * 30;
        AtomicInteger done = new AtomicInteger();
        runParallel(todo, i -> {
            Random random = new Random(1000L + existing + i);
            int storeIndex = random.nextInt(2);
            Store store = stores.get(storeIndex);
            List<Long> customers = customersByStore.get(storeIndex);
            Long customerId = null;
            if (!customers.isEmpty() && random.nextInt(100) >= 10) {
                // 앞쪽 고객이 더 자주 사서 등급이 골고루 나온다
                int index = (int) (customers.size() * Math.pow(random.nextDouble(), 2.2));
                customerId = customers.get(index);
            }
            List<Map<String, Object>> lines = new ArrayList<>();
            for (int l = 0, n = 1 + random.nextInt(3); l < n; l++) {
                Sku sku = skus.get(random.nextInt(skus.size()));
                int qty = 1 + (random.nextInt(10) == 0 ? 1 : 0);
                lines.add(Map.of("skuId", sku.id(), "qty", qty, "lineAmount", sku.price() * qty));
            }
            LocalDate day = today.minusDays(1 + random.nextInt(days));
            OffsetDateTime soldAt = day.atTime(10 + random.nextInt(11), random.nextInt(60))
                    .atOffset(ZoneOffset.ofHours(9));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sellerStaffId", store.staffIds().get(random.nextInt(store.staffIds().size())));
            body.put("customerId", customerId);
            body.put("lines", lines);
            body.put("soldAt", soldAt.toString());
            SeedApi.Response res = api.post("/sales", store.token(), body, UUID.randomUUID().toString());
            if (res.status() != 201) {
                throw new IllegalStateException("판매 기록 실패 status=" + res.status());
            }
            int n = done.incrementAndGet();
            if (n % 2000 == 0) {
                log.warn("seed 판매 진행 {}/{}", n, todo);
            }
        });
    }

    /** outbox 이벤트가 모두 처리될 때까지(대기·실패 0) 기다린다. 최대 30분. */
    private void waitForEvents() {
        for (int i = 0; i < 1800; i++) {
            int pending = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = 'PENDING'", Integer.class);
            if (pending == 0) {
                int failed = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = 'FAILED'", Integer.class);
                log.warn("seed 이벤트 처리 끝 failed={}", failed);
                return;
            }
            if (i % 30 == 0) {
                log.warn("seed 이벤트 처리 중 pending={}", pending);
            }
            sleep(1000);
        }
        log.error("seed 이벤트가 30분 안에 끝나지 않았다");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void runParallel(int total, java.util.function.IntConsumer task) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                int index = i;
                futures.add(pool.submit(() -> task.accept(index)));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("생성 중 오류", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
