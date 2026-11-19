package com.crmpos.sales.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.sales.domain.OutboxStatus;
import com.crmpos.sales.repository.OutboxEventRepository;
import com.crmpos.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 스케줄을 켠 채로 판매를 기록하면 곧(1초 주기) 이벤트가 PUBLISHED로 바뀌는 연결을 확인한다 (B2-4 완료 기준). */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"crm.outbox.publisher.enabled=true", "crm.outbox.publisher.interval-ms=200"})
@Import(OutboxSchedulerTest.ListenerConfig.class)
class OutboxSchedulerTest {

    @TestConfiguration
    static class ListenerConfig {
        @Bean
        RecordingListener schedulerListener() {
            return new RecordingListener();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OutboxEventRepository repository;
    @Autowired
    RecordingListener listener;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM sale_line");
        jdbc.update("UPDATE sale SET root_sale_id = NULL, origin_sale_id = NULL");
        jdbc.update("DELETE FROM sale");
        jdbc.update("DELETE FROM receipt_sequence");
        listener.reset();
    }

    @Test
    @DisplayName("판매를 기록하면 스케줄러가 곧 이벤트를 보내 PUBLISHED로 바꾼다")
    void should_publishSaleEventSoon() throws Exception {
        Long seller = jdbc.queryForObject("SELECT id FROM staff WHERE login_id = 'gn01-senior'", Long.class);
        String body = """
                {"sellerStaffId":%d,"lines":[{"productName":"머플러","itemType":"ACC","size":"FREE","qty":1,"lineAmount":9000}]}"""
                .formatted(seller);

        mockMvc.perform(post("/api/v1/sales").header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findAll()).singleElement()
                        .satisfies(e -> assertThat(e.getStatus()).isEqualTo(OutboxStatus.PUBLISHED)));
        assertThat(listener.received).hasSize(1);
        assertThat(listener.received.get(0).eventType()).isEqualTo("SALE_COMPLETED");
    }
}
