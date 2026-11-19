package com.crmpos.common.repository;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findFirstByRoleAndActiveTrueOrderByIdAsc(StaffRole role);

    /** 매장의 모든 계정(매장 계정·직원·키오스크), 등록 순서대로. */
    List<Staff> findByStoreIdOrderByIdAsc(Long storeId);

    /** 매장의 활성 계정 중 지정한 역할만 (판매 사원 고르기용). */
    List<Staff> findByStoreIdAndActiveTrueAndRoleInOrderByIdAsc(Long storeId, Collection<StaffRole> roles);

    /** 매장별 활성 직원 수. 직원 직급(MANAGER~STAFF)만 센다. 직원이 없는 매장은 결과에 없다. */
    @Query("select s.store.id as storeId, count(s) as count from Staff s "
            + "where s.active = true and s.role in :roles and s.store is not null group by s.store.id")
    List<StaffCount> countActiveByStore(Collection<StaffRole> roles);

    interface StaffCount {
        Long getStoreId();

        long getCount();
    }
}
