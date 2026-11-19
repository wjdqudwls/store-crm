package com.crmpos.sales.repository;

import com.crmpos.sales.domain.ReceiptSequence;
import com.crmpos.sales.domain.ReceiptSequenceId;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, ReceiptSequenceId> {

    /**
     * (매장, 날짜)의 순번을 한 문장으로 1 올린다. 행이 없으면 1로 만든다. 이 문장이 행을 잠그므로(트랜잭션이 끝날 때까지)
     * 같은 매장·날짜의 다른 요청은 기다렸다가 다음 번호를 받는다. 따로 잠금을 읽지 않아 첫 행을 동시에 만들 때의 교착이 없다.
     */
    @Modifying
    @Query(value = "INSERT INTO receipt_sequence (store_id, sale_date, last_no) VALUES (:storeId, :saleDate, 1) "
            + "ON DUPLICATE KEY UPDATE last_no = last_no + 1", nativeQuery = true)
    void increment(@Param("storeId") Long storeId, @Param("saleDate") LocalDate saleDate);

    /** 방금 올린 순번을 읽는다. 같은 트랜잭션이라 내가 올린 값이 보이고, 행은 내가 잠그고 있다. */
    @Query(value = "SELECT last_no FROM receipt_sequence WHERE store_id = :storeId AND sale_date = :saleDate",
            nativeQuery = true)
    int currentNo(@Param("storeId") Long storeId, @Param("saleDate") LocalDate saleDate);
}
