package com.crmpos.sales.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 발행기 스케줄을 켠다. 테스트는 {@code crm.outbox.publisher.enabled=false}로 꺼서 직접 부른다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "crm.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfig {
}
