package com.crmpos.customer.service;

import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.GradePolicy;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.customer.repository.GradePolicyRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 고객과 고객 요약을 한 트랜잭션에서 저장한다 (02-erd.md 3.4: 요약 1:1을 늘 지킨다). */
@Component
@RequiredArgsConstructor
public class CustomerWriter {

    private final CustomerRepository customerRepository;
    private final CustomerSummaryRepository summaryRepository;
    private final GradePolicyRepository policyRepository;

    @Transactional
    public Customer insert(CustomerDraft draft, LocalDateTime now) {
        int policyVersion = policyRepository
                .findFirstByAppliesFromLessThanEqualOrderByVersionDesc(LocalDate.from(now))
                .map(GradePolicy::getVersion)
                .orElseThrow(() -> new IllegalStateException("적용 중인 등급 기준이 없습니다."));
        Customer customer = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.save(CustomerSummary.initial(customer.getId(), policyVersion, now));
        return customer;
    }
}
