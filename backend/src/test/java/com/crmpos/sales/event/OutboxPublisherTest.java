package com.crmpos.sales.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.event.OutboxMessage;
import com.crmpos.sales.domain.OutboxEvent;
import com.crmpos.sales.domain.OutboxStatus;
import com.crmpos.sales.domain.SaleEventType;
import com.crmpos.sales.repository.OutboxEventRepository;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * outbox 발행기를 실제 MariaDB로 확인한다 (SAL-02, BR-06, ADR-007). 스케줄러는 끄고 {@code publishPending()}을 직접 부른다.
 * 트랜잭션 없이 실제로 커밋하고, 끝나면 만든 행을 지운다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@Import(OutboxPublisherTest.ListenerConfig.class)
class OutboxPublisherTest {

    @TestConfiguration
    static class ListenerConfig {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 19, 10, 0, 0);

    @Autowired
    OutboxPublisher publisher;
    @Autowired
    OutboxEventRepository repository;
    @Autowired
    RecordingListener listener;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        listener.reset();
    }

    private void save(String id, long aggregateId, int secondsAfterT0) {
        repository.saveAndFlush(OutboxEvent.pending(id, SaleEventType.SALE_COMPLETED, aggregateId,
                "{\"saleId\":" + aggregateId + ",\"memo\":\"고객 메모\"}", T0.plusSeconds(secondsAfterT0)));
    }

    private OutboxEvent find(String id) {
        return repository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("보낼 이벤트는 PUBLISHED가 되고 받는 쪽은 종류·판매·payload가 든 OutboxMessage를 받는다")
    void should_publishPendingEvent() {
        save("e1", 100L, 0);

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        OutboxEvent event = find("e1");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(listener.received).hasSize(1);
        OutboxMessage message = listener.received.get(0);
        assertThat(message.eventId()).isEqualTo("e1");
        assertThat(message.eventType()).isEqualTo("SALE_COMPLETED");
        assertThat(message.aggregateId()).isEqualTo(100L);
        // MariaDB가 JSON 공백을 정리해 돌려주므로 문자열이 아니라 JSON으로 읽어 확인한다
        assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(message.payload())
                .get("saleId").asLong()).isEqualTo(100L);
    }

    @Test
    @DisplayName("이미 보낸 이벤트는 다시 보내지 않는다")
    void should_notRepublish() {
        save("e1", 100L, 0);

        publisher.publishPending();
        publisher.publishPending();

        assertThat(listener.receivedIds()).containsExactly("e1");
    }

    @Test
    @DisplayName("오래된 이벤트부터 보낸다")
    void should_publishOldestFirst() {
        save("c", 3L, 30);
        save("a", 1L, 10);
        save("b", 2L, 20);

        publisher.publishPending();

        assertThat(listener.receivedIds()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("같은 판매의 앞 이벤트가 끝나지 않았으면 뒤 이벤트는 보내지 않고 건너뛴다")
    void should_skipLaterEvent_whenEarlierOfSameAggregateNotPublished() {
        save("first", 100L, 0);
        save("second", 100L, 10);
        listener.failing.add("first");

        publisher.publishPending();

        assertThat(listener.receivedIds()).containsExactly("first");
        assertThat(find("first").getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(find("second").getStatus()).isEqualTo(OutboxStatus.PENDING);

        listener.failing.clear();
        publisher.publishPending();
        publisher.publishPending();

        assertThat(listener.receivedIds()).containsExactly("first", "first", "second");
        assertThat(find("second").getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("한 판매가 막혀도 다른 판매의 이벤트는 계속 나간다")
    void should_continueOtherAggregates_whenOneBlocked() {
        save("a1", 1L, 0);
        save("a2", 1L, 5);
        save("b1", 2L, 10);
        listener.failing.add("a1");

        publisher.publishPending();

        assertThat(find("b1").getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(find("a1").getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(listener.receivedIds()).doesNotContain("a2");
    }

    @Test
    @DisplayName("실패할 때마다 시도 횟수가 오르고 9번째까지는 PENDING, 10번째 실패에서 FAILED가 된다")
    void should_markFailedAfterTenAttempts() {
        save("bad", 100L, 0);
        listener.failing.add("bad");

        for (int i = 1; i <= 9; i++) {
            publisher.publishPending();
            assertThat(find("bad").getAttempts()).isEqualTo(i);
            assertThat(find("bad").getStatus()).isEqualTo(OutboxStatus.PENDING);
        }
        publisher.publishPending();

        assertThat(find("bad").getAttempts()).isEqualTo(10);
        assertThat(find("bad").getStatus()).isEqualTo(OutboxStatus.FAILED);
        publisher.publishPending();
        assertThat(listener.received).hasSize(10);
    }

    @Test
    @DisplayName("FAILED가 된 이벤트가 있으면 같은 판매의 뒤 이벤트는 계속 막혀 있고, 다른 판매는 영향이 없다")
    void should_keepBlocking_afterFailed() {
        save("bad", 100L, 0);
        save("later", 100L, 10);
        save("other", 200L, 20);
        listener.failing.add("bad");

        for (int i = 0; i < 12; i++) {
            publisher.publishPending();
        }

        assertThat(find("bad").getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(find("later").getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(find("other").getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(listener.receivedIds()).doesNotContain("later");
    }

    @Test
    @DisplayName("받는 쪽이 실패하면 그 처리는 되돌려지고 이벤트는 PUBLISHED가 아니다")
    void should_notMarkPublished_whenListenerFails() {
        save("bad", 100L, 0);
        listener.failing.add("bad");

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(find("bad").getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(find("bad").getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("발행기가 동시에 두 번 돌아도 같은 이벤트는 한 번만 나간다")
    void should_deliverOnce_whenRunsOverlap() throws Exception {
        for (int i = 0; i < 20; i++) {
            save("ev" + String.format("%02d", i), 1000L + i, i);
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return publisher.publishPending();
            }));
        }
        go.countDown();
        int total = 0;
        for (Future<Integer> future : futures) {
            total += future.get();
        }
        pool.shutdown();

        assertThat(total).isEqualTo(20);
        assertThat(listener.receivedIds()).hasSize(20).doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE status = 'PUBLISHED'", Integer.class))
                .isEqualTo(20);
    }

    @Test
    @DisplayName("보낸 이벤트 개수 상한(50)을 넘으면 다음 번에 이어서 보낸다")
    void should_publishInBatches() {
        for (int i = 0; i < 55; i++) {
            save("b" + String.format("%02d", i), 5000L + i, i);
        }

        assertThat(publisher.publishPending()).isEqualTo(50);
        assertThat(publisher.publishPending()).isEqualTo(5);
        assertThat(listener.receivedIds()).hasSize(55);
    }

    @Test
    @DisplayName("OutboxMessage의 toString은 payload(고객 메모)를 보이지 않는다")
    void should_maskPayload_inToString() {
        OutboxMessage message = new OutboxMessage("e1", "SALE_COMPLETED", 1L, "{\"memo\":\"고객 메모\"}");

        assertThat(message.toString()).doesNotContain("고객 메모");
    }

    @Test
    @DisplayName("10번 실패해 FAILED가 되면 ERROR 로그에 eventId가 남고, 9번째까지는 ERROR가 아니며, payload(고객 메모)는 남지 않는다")
    void should_logErrorWithEventId_whenFailed(CapturedOutput output) {
        save("bad-event-id", 100L, 0);
        listener.failing.add("bad-event-id");

        for (int i = 0; i < 9; i++) {
            publisher.publishPending();
        }
        assertThat(output.getAll()).doesNotContain("FAILED after");

        publisher.publishPending();

        assertThat(output.getAll()).contains("ERROR").contains("outbox event FAILED after 10 attempts")
                .contains("eventId=bad-event-id").contains("aggregateId=100");
        assertThat(output.getAll()).doesNotContain("고객 메모");
    }
}
