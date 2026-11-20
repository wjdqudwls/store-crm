package com.crmpos.customer.repository;

import java.time.LocalDateTime;

/** 판매의 판매 시각 (customer_purchase.sold_at). 오늘 할 일에 기준 판매 시각으로 보인다. */
public record SaleSoldAt(Long saleId, LocalDateTime soldAt) {
}
