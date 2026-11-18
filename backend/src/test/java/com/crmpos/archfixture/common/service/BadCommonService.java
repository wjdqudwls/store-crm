package com.crmpos.archfixture.common.service;

import com.crmpos.archfixture.sales.service.GoodSalesService;

/** common이 다른 모듈을 import한다 (ADR-012 변경). 경계 규칙이 잡아야 한다. */
public class BadCommonService {
    GoodSalesService sales;
}
