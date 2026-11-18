package com.crmpos.common.repository;

import com.crmpos.common.domain.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCode(String code);
}
