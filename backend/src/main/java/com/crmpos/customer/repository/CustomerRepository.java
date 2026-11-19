package com.crmpos.customer.repository;

import com.crmpos.customer.domain.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByPhoneHash(String phoneHash);

    Optional<Customer> findByPhoneHash(String phoneHash);
}
