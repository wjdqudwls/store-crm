package com.crmpos.archfixture.sales.service;

import com.crmpos.archfixture.customer.api.FixtureCustomerApi;

/** 다른 모듈은 api로만 부른다. 경계 규칙을 통과해야 한다. */
public class GoodSalesService {
    FixtureCustomerApi customerApi;
}
