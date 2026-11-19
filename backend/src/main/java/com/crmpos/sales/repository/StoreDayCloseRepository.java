package com.crmpos.sales.repository;

import com.crmpos.sales.domain.StoreDayClose;
import com.crmpos.sales.domain.StoreDayCloseId;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreDayCloseRepository extends JpaRepository<StoreDayClose, StoreDayCloseId> {

    /** 마감 행을 잠그고 읽는다 (마감·다시 열기·다시 마감이 겹쳐도 한 줄씩 처리한다). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StoreDayClose c where c.id = :id")
    Optional<StoreDayClose> findForUpdate(@Param("id") StoreDayCloseId id);

    /** 마감 행이 있는 날만, 최신 날짜 순. */
    @Query("select c from StoreDayClose c where c.id.storeId = :storeId and c.id.businessDate between :from and :to "
            + "order by c.id.businessDate desc")
    List<StoreDayClose> findByStoreAndPeriod(@Param("storeId") Long storeId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
