package com.crmpos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 영수증 순번. (매장, 날짜) 행을 잠그고 1 올린 값을 쓴다 (02-erd.md 3.3). */
@Entity
@Table(name = "receipt_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptSequence {

    @EmbeddedId
    private ReceiptSequenceId id;

    @Column(nullable = false)
    private int lastNo;
}
