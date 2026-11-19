package com.crmpos.sales.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.sales.domain.OutboxEvent;
import com.crmpos.sales.domain.OutboxStatus;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleEventType;
import com.crmpos.sales.domain.SaleLine;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** SALE_COMPLETED v1 이벤트가 03-api.md 6장의 계약과 같은 모양인지 확인한다 (ADR-002). */
class SaleEventFactoryTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SaleEventFactory factory = new SaleEventFactory(mapper);
    private static final LocalDateTime SOLD_AT = LocalDateTime.of(2026, 10, 15, 14, 3);

    private Sale sale() {
        SaleLine registered = SaleLine.of(5501L, "D2110", "울 블렌드 롱코트", Target.WOMEN, ItemType.OUTER, "차콜", "90",
                798000L, 1, 774060, null, null);
        SaleLine direct = SaleLine.of(null, null, "머플러", null, ItemType.ACC, "오트밀", "FREE", null, 1, 217800, 31L, "남편");
        Sale sale = Sale.record(1L, "GN01-20261015-0012", "k", "h", 17L, 3L, 1024L, GradeCode.B, SOLD_AT, SOLD_AT,
                List.of(registered, direct));
        ReflectionTestUtils.setField(sale, "id", 88231L);
        ReflectionTestUtils.setField(sale.getLines().get(0), "id", 1L);
        ReflectionTestUtils.setField(sale.getLines().get(1), "id", 2L);
        sale.assignRootToSelf();
        return sale;
    }

    @Test
    @DisplayName("outbox 이벤트는 PENDING으로 시작하고 묶음 키(aggregate)는 처음 판매 ID다")
    void should_createPendingEventWithRootAggregate() {
        OutboxEvent event = factory.completed(sale(), "메모", SOLD_AT);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getEventType()).isEqualTo(SaleEventType.SALE_COMPLETED);
        assertThat(event.getAggregateId()).isEqualTo(88231L);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getEventId()).hasSize(36);
    }

    @Test
    @DisplayName("본문은 계약의 칸을 모두 담는다")
    void should_matchContractFields() {
        OutboxEvent event = factory.completed(sale(), "요청했던 롱코트 구매", SOLD_AT);

        JsonNode json = mapper.readTree(event.getPayload());
        assertThat(json.get("eventId").asString()).isEqualTo(event.getEventId());
        assertThat(json.get("eventType").asString()).isEqualTo("SALE_COMPLETED");
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-15T14:03:00+09:00");
        assertThat(json.get("saleId").asLong()).isEqualTo(88231L);
        assertThat(json.get("receiptNo").asString()).isEqualTo("GN01-20261015-0012");
        assertThat(json.get("storeId").asLong()).isEqualTo(1L);
        assertThat(json.get("customerId").asLong()).isEqualTo(1024L);
        assertThat(json.get("staffId").asLong()).isEqualTo(17L);
        assertThat(json.get("recordedBy").asLong()).isEqualTo(3L);
        assertThat(json.get("kind").asString()).isEqualTo("NORMAL");
        assertThat(json.get("originSaleId").isNull()).isTrue();
        assertThat(json.get("rootSaleId").asLong()).isEqualTo(88231L);
        assertThat(json.get("exchange").isNull()).isTrue();
        assertThat(json.get("memo").asString()).isEqualTo("요청했던 롱코트 구매");
        assertThat(json.get("lines")).hasSize(2);
    }

    @Test
    @DisplayName("라인: 대상·착용자를 모르면 그 칸이 빠지고, 있으면 들어간다")
    void should_omitTargetAndMemberWhenUnknown() {
        JsonNode json = mapper.readTree(factory.completed(sale(), null, SOLD_AT).getPayload());

        JsonNode first = json.get("lines").get(0);
        JsonNode second = json.get("lines").get(1);
        assertThat(first.get("saleLineId").asLong()).isEqualTo(1L);
        assertThat(first.get("target").asString()).isEqualTo("WOMEN");
        assertThat(first.get("itemType").asString()).isEqualTo("OUTER");
        assertThat(first.get("qty").asInt()).isEqualTo(1);
        assertThat(first.get("lineAmount").asLong()).isEqualTo(774060L);
        assertThat(first.has("memberId")).isFalse();
        assertThat(second.has("target")).isFalse();
        assertThat(second.get("memberId").asLong()).isEqualTo(31L);
    }

    @Test
    @DisplayName("비회원 판매의 이벤트도 만들고 customerId는 null이다 (BR-06)")
    void should_createEvent_forGuestSale() {
        SaleLine line = SaleLine.of(null, null, "양말", null, ItemType.ACC, null, "FREE", null, 1, 9000, null, null);
        Sale guest = Sale.record(1L, "GN01-20261015-0013", "k2", "h", 17L, 3L, null, null, SOLD_AT, SOLD_AT, List.of(line));
        ReflectionTestUtils.setField(guest, "id", 88232L);
        guest.assignRootToSelf();

        JsonNode json = mapper.readTree(factory.completed(guest, null, SOLD_AT).getPayload());

        assertThat(json.get("customerId").isNull()).isTrue();
        assertThat(json.get("memo").isNull()).isTrue();
    }

    @Test
    @DisplayName("이벤트 본문의 toString에는 메모가 보이지 않는다")
    void should_maskMemo_inPayloadToString() {
        SaleCompletedPayload payload = new SaleCompletedPayload("e", "SALE_COMPLETED", 1, "t", 1L, "r", 1L, 2L, 3L, 4L,
                "NORMAL", null, 1L, null, "개인적인 메모", List.of());

        assertThat(payload.toString()).doesNotContain("개인적인");
    }
}
