package com.crmpos.archfixture.sales.service;

import com.crmpos.archfixture.customer.domain.FixtureCustomer;

/** 일부러 다른 모듈의 domain을 import한다. 경계 규칙이 이것을 잡아야 한다. */
public class BadSalesService {
    FixtureCustomer customer;
}
