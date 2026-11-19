package com.crmpos.customer.repository;

import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.NoteType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultNoteRepository extends JpaRepository<ConsultNote, Long> {

    List<ConsultNote> findBySaleIdAndType(Long saleId, NoteType type);
}
