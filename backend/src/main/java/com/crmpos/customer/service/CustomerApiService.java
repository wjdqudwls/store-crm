package com.crmpos.customer.service;

import com.crmpos.common.type.GradeCode;
import com.crmpos.customer.api.CustomerApi;
import com.crmpos.customer.api.SaleCustomer;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerApiService implements CustomerApi {

    private final EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<SaleCustomer> findForSale(Long customerId) {
        List<GradeCode> grades = em.createQuery(
                        "select s.currentGrade from CustomerSummary s where s.customerId = :id", GradeCode.class)
                .setParameter("id", customerId).getResultList();
        return grades.stream().findFirst().map(grade -> new SaleCustomer(customerId, grade));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findMemberNicknames(Long customerId, Collection<Long> memberIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = em.createQuery(
                        "select m.id, m.nickname from CustomerMember m where m.customer.id = :cid and m.id in :ids",
                        Object[].class)
                .setParameter("cid", customerId).setParameter("ids", memberIds).getResultList();
        for (Object[] row : rows) {
            result.put((Long) row[0], (String) row[1]);
        }
        return result;
    }
}
