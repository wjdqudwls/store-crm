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
public class StoreDayClose {

    @EmbeddedId
    private StoreDayCloseId id;

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
}
