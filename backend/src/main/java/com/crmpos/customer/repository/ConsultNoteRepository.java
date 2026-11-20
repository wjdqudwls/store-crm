package com.crmpos.customer.repository;

import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.NoteType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultNoteRepository extends JpaRepository<ConsultNote, Long> {

    List<ConsultNote> findBySaleIdAndType(Long saleId, NoteType type);

    /** 고객의 메모를 최신순으로 (같은 시각이면 나중에 만든 것 먼저). */
    Page<ConsultNote> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);
}
