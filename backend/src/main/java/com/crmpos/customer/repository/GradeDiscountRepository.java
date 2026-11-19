package com.crmpos.customer.repository;

import com.crmpos.common.type.GradeCode;
import com.crmpos.customer.domain.GradeDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeDiscountRepository extends JpaRepository<GradeDiscount, GradeCode> {
}
