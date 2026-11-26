package com.crmpos.common.repository;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByLoginId(String loginId);

    /** QR의 직원 코드로 그 매장의 직원 찾기 (REG-03). */
    Optional<Staff> findFirstByStoreIdAndStaffCode(Long storeId, String staffCode);

    Optional<Staff> findFirstByRoleAndActiveTrueOrderByIdAsc(StaffRole role);

    /** 매장의 모든 계정(매장 계정·직원·키오스크), 등록 순서대로. */
    List<Staff> findByStoreIdOrderByIdAsc(Long storeId);

    /** 매장의 활성 계정 중 지정한 역할만 (판매 사원 고르기용). */
    List<Staff> findByStoreIdAndActiveTrueAndRoleInOrderByIdAsc(Long storeId, Collection<StaffRole> roles);

    /** 한 매장의 활성 직원 수 (직급 계정만). */
    long countByStoreIdAndActiveTrueAndRoleIn(Long storeId, Collection<StaffRole> roles);

    /** 매장별 활성 직원 수. 직원 직급(MANAGER~STAFF)만 센다. 직원이 없는 매장은 결과에 없다. */
    @Query("select s.store.id as storeId, count(s) as count from Staff s "
            + "where s.active = true and s.role in :roles and s.store is not null group by s.store.id")
    List<StaffCount> countActiveByStore(Collection<StaffRole> roles);

    interface StaffCount {
        Long getStoreId();

        long getCount();
    }

    /** 로그인 실패 횟수를 1 올린다 (한 문장 UPDATE라 동시에 틀려도 세어진다). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Staff s set s.failedLoginCount = s.failedLoginCount + 1 where s.id = :id")
    int addLoginFailure(@Param("id") Long id);

    /** 실패가 limit번 이상 쌓였으면 until까지 잠그고 횟수를 0으로 되돌린다. 잠갔으면 1. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Staff s set s.lockedUntil = :until, s.failedLoginCount = 0 "
            + "where s.id = :id and s.failedLoginCount >= :limit")
    int lockIfFailuresReached(@Param("id") Long id, @Param("limit") int limit, @Param("until") LocalDateTime until);
}
