package com.crmpos.customer.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 다른 모듈이 고객 데이터를 읽는 창구 (ADR-012). 바꾸는 일은 outbox 이벤트로만 한다. */
public interface CustomerApi {

    /** 없는 고객이면 비어 있다. */
    Optional<SaleCustomer> findForSale(Long customerId);

    /** 그 고객의 구성원만 (ID → 호칭). 다른 고객의 구성원이거나 없는 ID는 결과에 없다 (CUS-05). */
    Map<Long, String> findMemberNicknames(Long customerId, Collection<Long> memberIds);

    /** 고객 ID → 이름. 판매 내역 목록이 이름을 한 번에 읽는다(목록 N+1 방지). 없는 ID는 결과에 없다. */
    Map<Long, String> findNames(Collection<Long> customerIds);
}
