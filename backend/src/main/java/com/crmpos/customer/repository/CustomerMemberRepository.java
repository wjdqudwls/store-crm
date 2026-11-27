package com.crmpos.customer.repository;

import com.crmpos.customer.domain.CustomerMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerMemberRepository extends JpaRepository<CustomerMember, Long> {

    /** 한 고객의 구성원, 등록 순서대로. */
    List<CustomerMember> findByCustomerIdOrderByIdAsc(Long customerId);
}
