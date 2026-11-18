package com.crmpos.common.repository;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findFirstByRoleAndActiveTrueOrderByIdAsc(StaffRole role);
}
