package com.crmpos.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 할 일 한 건의 상태 규칙 (BR-14): 열린 것만 한 번 닫히고, 닫힌 것은 다시 열리지 않는다. */
class FollowUpTaskTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDate DUE = LocalDate.of(2026, 11, 22);

    private FollowUpTask saleTask() {
        FollowUpRule rule = FollowUpRule.create("감사 인사", 2, FollowUpOffsetUnit.DAY, false, 1, true, NOW);
        return FollowUpTask.forSale(null, 17L, 88231L, rule, DUE, NOW);
    }

    @Test
    @DisplayName("판매 할 일은 단계 이름·홍보 여부를 복사해 열린 상태로 시작한다")
    void should_copyRuleSnapshot_whenCreatedForSale() {
        FollowUpRule rule = FollowUpRule.create("신상·재방문 안내", 2, FollowUpOffsetUnit.MONTH, true, 3, true, NOW);

        FollowUpTask task = FollowUpTask.forSale(null, 17L, 88231L, rule, DUE, NOW);
        rule.apply("바뀐 이름", 1, FollowUpOffsetUnit.DAY, false, 3, true, NOW);

        assertThat(task.getRuleName()).isEqualTo("신상·재방문 안내");
        assertThat(task.isPromotional()).isTrue();
        assertThat(task.getKind()).isEqualTo(FollowUpKind.SALE);
        assertThat(task.isOpen()).isTrue();
    }

    @Test
    @DisplayName("수동 할 일은 방문 예정·다시 연락 이름에 홍보가 아니고 기준 판매가 없다")
    void should_createManualTask_withoutSale() {
        FollowUpTask visit = FollowUpTask.manual(null, 17L, FollowUpKind.VISIT, 7001L, DUE, NOW);
        FollowUpTask recall = FollowUpTask.manual(null, 17L, FollowUpKind.RECALL, 7002L, DUE, NOW);

        assertThat(visit.getRuleName()).isEqualTo("방문 예정");
        assertThat(recall.getRuleName()).isEqualTo("다시 연락");
        assertThat(visit.getSaleId()).isNull();
        assertThat(visit.isPromotional()).isFalse();
        assertThatThrownBy(() -> FollowUpTask.manual(null, 17L, FollowUpKind.SALE, 1L, DUE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("완료·건너뛰기·취소·대체는 각각 상태와 닫힌 시각을 정하고, 한 번 닫히면 다시 닫을 수 없다")
    void should_closeOnlyOnce() {
        FollowUpTask done = saleTask();
        done.complete(501L, NOW);
        assertThat(done.getStatus()).isEqualTo(FollowUpTaskStatus.DONE);
        assertThat(done.getNoteId()).isEqualTo(501L);
        assertThat(done.getClosedAt()).isEqualTo(NOW);

        FollowUpTask skipped = saleTask();
        skipped.skip(NOW);
        assertThat(skipped.getStatus()).isEqualTo(FollowUpTaskStatus.SKIPPED);

        FollowUpTask cancelled = saleTask();
        cancelled.cancel(NOW);
        assertThat(cancelled.getStatus()).isEqualTo(FollowUpTaskStatus.CANCELLED);

        FollowUpTask replaced = saleTask();
        replaced.replace(NOW);
        assertThat(replaced.getStatus()).isEqualTo(FollowUpTaskStatus.REPLACED);

        assertThatThrownBy(() -> done.skip(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cancelled.replace(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("'방문함'은 방문 예약(VISIT)만 된다")
    void should_markVisited_onlyForVisit() {
        FollowUpTask visit = FollowUpTask.manual(null, 17L, FollowUpKind.VISIT, 7001L, DUE, NOW);
        visit.markVisited(NOW);
        assertThat(visit.getStatus()).isEqualTo(FollowUpTaskStatus.DONE);
        assertThat(visit.getNoteId()).isNull();

        assertThatThrownBy(() -> saleTask().markVisited(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("받는 직원은 열린 할 일만 바꿀 수 있다 (퇴사 이관)")
    void should_reassign_onlyOpen() {
        FollowUpTask task = saleTask();
        task.reassign(23L);
        assertThat(task.getStaffId()).isEqualTo(23L);

        task.skip(NOW);
        assertThatThrownBy(() -> task.reassign(24L)).isInstanceOf(IllegalStateException.class);
    }
}
