package com.crmpos.sales.event;

import com.crmpos.common.event.OutboxMessage;
import com.crmpos.common.web.KstTime;
import com.crmpos.sales.domain.OutboxEvent;
import com.crmpos.sales.domain.OutboxStatus;
import com.crmpos.sales.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * outbox 발행기 (SAL-02, BR-06, ADR-002·007). 1초마다 보낼 수 있는 이벤트를 오래된 순으로 읽어 {@link OutboxMessage}
 * Spring 이벤트로 보낸다. 같은 판매의 앞 이벤트가 끝나지 않았으면 뒤 이벤트는 건너뛴다.
 *
 * <p>이벤트 하나의 전달과 기록은 한 트랜잭션이다: 받는 쪽(고객 모듈)의 저장도 같은 트랜잭션이라 함께 커밋되거나 함께
 * 롤백된다. 실패하면 그 트랜잭션을 되돌리고, 따로 시도 횟수를 올린다(10번째 실패에서 FAILED).
 * 로그에는 이벤트 ID·종류·시도 횟수·예외 종류만 남긴다(payload에 고객 메모가 있을 수 있다, NFR-04b).
 */
@Slf4j
@Component
public class OutboxPublisher {

    static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate deliverTx;
    private final TransactionTemplate recordTx;

    public OutboxPublisher(OutboxEventRepository repository, ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        // MariaDB 11의 스냅샷 격리로 "Record has changed since last read"가 나지 않게 READ_COMMITTED (ADR-038)
        this.deliverTx = new TransactionTemplate(transactionManager);
        this.deliverTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.deliverTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.recordTx = new TransactionTemplate(transactionManager);
        this.recordTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.recordTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 스케줄러가 부른다. 앞 실행이 끝난 뒤 1초 쉬고 다시 돌아서(fixedDelay) 한 번에 한 번만 돈다. */
    @Scheduled(fixedDelayString = "${crm.outbox.publisher.interval-ms:1000}")
    public void run() {
        try {
            publishPending();
        } catch (RuntimeException e) {
            // 발행기가 죽어도 다음 주기에 다시 돈다. 예외 메시지에 값이 들어갈 수 있어 종류만 남긴다.
            log.error("outbox publisher run failed type={}", e.getClass().getSimpleName());
        }
    }

    /** 지금 보낼 수 있는 이벤트를 한 번 훑어 보낸다. 보낸 개수를 돌려준다. */
    public int publishPending() {
        List<String> eventIds = repository.findDeliverable(PageRequest.of(0, BATCH_SIZE)).stream()
                .map(OutboxEvent::getEventId).toList();
        int published = 0;
        for (String eventId : eventIds) {
            if (publishOne(eventId)) {
                published++;
            }
        }
        return published;
    }

    private boolean publishOne(String eventId) {
        try {
            Boolean delivered = deliverTx.execute(status -> deliver(eventId));
            return Boolean.TRUE.equals(delivered);
        } catch (RuntimeException e) {
            recordFailure(eventId, e);
            return false;
        }
    }

    /** 잠그고 다시 확인한 뒤 보낸다. 이미 보냈거나 앞 이벤트가 남아 있으면 false (건너뜀). */
    private boolean deliver(String eventId) {
        OutboxEvent event = repository.findForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return false;
        }
        if (repository.existsEarlierUnpublished(event.getAggregateId(), event.getCreatedAt(), event.getEventId())) {
            return false;
        }
        eventPublisher.publishEvent(new OutboxMessage(event.getEventId(), event.getEventType().name(),
                event.getAggregateId(), event.getPayload()));
        event.markPublished(LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS));
        return true;
    }

    /** 받는 쪽이 실패해 전달 트랜잭션이 롤백된 뒤, 따로 시도 횟수를 올린다. */
    private void recordFailure(String eventId, RuntimeException cause) {
        recordTx.executeWithoutResult(status -> repository.findForUpdate(eventId).ifPresent(event -> {
            if (event.getStatus() != OutboxStatus.PENDING) {
                return;
            }
            event.recordFailure();
            log.warn("outbox event failed eventId={} type={} attempts={} status={} cause={}", event.getEventId(),
                    event.getEventType(), event.getAttempts(), event.getStatus(), cause.getClass().getSimpleName());
        }));
    }
}
