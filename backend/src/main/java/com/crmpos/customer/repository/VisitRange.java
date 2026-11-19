package com.crmpos.customer.repository;

import java.time.LocalDateTime;

/** 고객의 첫·마지막 방문일. 구매가 없으면 둘 다 null. */
public record VisitRange(LocalDateTime first, LocalDateTime last) {
}
