package com.crmpos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 마감 (02-erd.md 3.3). 그날 판매는 날짜가 지났거나, 이 행이 있고 reopenedAt이 null이면 잠긴다.
 * 다시 연 날을 다시 마감하면 같은 행의 closed*를 새로 쓰고 reopened*를 비운다.
 */
@Entity
@Table(name = "store_day_close")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreDayClose implements org.springframework.data.domain.Persistable<StoreDayCloseId> {

    @EmbeddedId
    private StoreDayCloseId id;

    /** 방금 만든 행인지. 키를 직접 정해서 Spring Data가 새 행인지 알 수 없으니 알려 준다(아니면 merge가 되어 동시 마감이 덮어쓴다). */
    @jakarta.persistence.Transient
    private boolean newRow;

    /** 마감한 계정. common 모듈의 ID (ADR-003). */
    @Column(nullable = false)
    private Long closedBy;

    @Column(nullable = false)
    private LocalDateTime closedAt;

    /** 다시 연 본사 계정. */
    @Column
    private Long reopenedBy;

    /** null이 아니면 다시 열린 상태. */
    @Column
    private LocalDateTime reopenedAt;

    /** 그날 판매는 이 행이 있고 다시 열리지 않았으면 잠긴다 (BR-16). */
    public boolean isClosed() {
        return reopenedAt == null;
    }

    /** 하루를 마감한다 (SAL-08). */
    public static StoreDayClose close(Long storeId, java.time.LocalDate businessDate, Long closedBy,
            LocalDateTime closedAt) {
        StoreDayClose close = new StoreDayClose();
        close.id = new StoreDayCloseId(storeId, businessDate);
        close.closedBy = closedBy;
        close.closedAt = closedAt;
        close.newRow = true;
        return close;
    }

    /** 본사가 다시 연다. 이미 열려 있으면 처음 기록을 그대로 둔다. */
    public void reopen(Long reopenedBy, LocalDateTime reopenedAt) {
        if (reopenedAt != null && this.reopenedAt == null) {
            this.reopenedBy = reopenedBy;
            this.reopenedAt = reopenedAt;
        }
    }

    /** 다시 연 날을 다시 마감한다: 같은 행의 마감 계정·시각을 새로 쓰고 다시 열기 기록을 비운다 (02-erd.md 3.3). */
    public void closeAgain(Long closedBy, LocalDateTime closedAt) {
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.reopenedBy = null;
        this.reopenedAt = null;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }

    @jakarta.persistence.PostPersist
    @jakarta.persistence.PostLoad
    void markNotNew() {
        this.newRow = false;
    }
}
