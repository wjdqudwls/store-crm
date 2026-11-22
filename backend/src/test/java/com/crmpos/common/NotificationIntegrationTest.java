package com.crmpos.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.service.PushSender;
import com.crmpos.common.service.StaffNotifier;
import com.crmpos.common.domain.NotificationType;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.RecordingPushSender;
import com.crmpos.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 직원 알림함·알림 설정·웹 푸시 구독·푸시 보내기를 실제 MariaDB로 확인한다 (CAL-08·09·12). 롤백한다. 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@Import(NotificationIntegrationTest.PushConfigForTest.class)
class NotificationIntegrationTest {

    @TestConfiguration
    static class PushConfigForTest {
        @Bean
        @Primary
        RecordingPushSender recordingPushSender() {
            return new RecordingPushSender();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffNotifier notifier;
    @Autowired
    RecordingPushSender push;
    @jakarta.persistence.PersistenceContext
    jakarta.persistence.EntityManager em;

    @BeforeEach
    void reset() {
        push.reset();
    }

    private long id(String loginId) {
        return jdbc.queryForObject("SELECT id FROM staff WHERE login_id = ?", Long.class, loginId);
    }

    private ResultActions api(String account, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(id(account))));
    }

    private static final String SUB = "{\"endpoint\":\"https://push.example/abc\",\"keys\":{\"p256dh\":\"pk\",\"auth\":\"au\"}}";

    // --- 알림함 ---

    @Test
    @DisplayName("알림함은 안 읽은 것 먼저, 그 안에서 최신순이고 unreadCount가 배지 숫자다")
    void should_listUnreadFirst_newestFirst() throws Exception {
        long junior = id("gn01-junior");
        notifier.send(junior, NotificationType.FOLLOW_UP, 1L, 11L, "첫 알림");
        notifier.send(junior, NotificationType.NOTE, 2L, 12L, "둘째 알림");
        notifier.send(junior, NotificationType.FOLLOW_UP, 3L, 13L, "셋째 알림");
        em.flush();
        long first = jdbc.queryForObject("SELECT id FROM staff_notification WHERE message = '첫 알림'", Long.class);
        api("gn01-junior", post("/api/v1/notifications/" + first + "/read")).andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        api("gn01-junior", get("/api/v1/notifications")).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].message").value("셋째 알림")).andExpect(jsonPath("$.content[1].message").value("둘째 알림"))
                .andExpect(jsonPath("$.content[2].message").value("첫 알림")).andExpect(jsonPath("$.content[2].read").value(true))
                .andExpect(jsonPath("$.content[0].type").value("FOLLOW_UP")).andExpect(jsonPath("$.content[0].refId").value(13))
                .andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    @Test
    @DisplayName("내 알림만 읽음 처리: 없는 알림 404, 남의 알림 403, 이미 읽은 알림은 처음 읽은 시각 그대로")
    void should_markReadOnlyOwn() throws Exception {
        notifier.send(id("gn01-senior"), NotificationType.NOTE, 2L, 12L, "시니어 알림");
        notifier.send(id("gn01-junior"), NotificationType.NOTE, 2L, 12L, "주니어 알림");
        em.flush();
        long seniors = jdbc.queryForObject("SELECT id FROM staff_notification WHERE message = '시니어 알림'", Long.class);
        long juniors = jdbc.queryForObject("SELECT id FROM staff_notification WHERE message = '주니어 알림'", Long.class);

        api("gn01-junior", post("/api/v1/notifications/" + seniors + "/read")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        api("gn01-junior", post("/api/v1/notifications/999999/read")).andExpect(status().isNotFound());
        api("gn01-junior", post("/api/v1/notifications/" + juniors + "/read")).andExpect(status().isOk());
        em.flush();
        Object firstRead = jdbc.queryForObject("SELECT read_at FROM staff_notification WHERE id = ?", java.sql.Timestamp.class, juniors);
        api("gn01-junior", post("/api/v1/notifications/" + juniors + "/read")).andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT read_at FROM staff_notification WHERE id = ?", java.sql.Timestamp.class, juniors))
                .isEqualTo(firstRead);
        api("gn01-senior", get("/api/v1/notifications")).andExpect(jsonPath("$.unreadCount").value(1)); // 시니어 것은 그대로
    }

    @Test
    @DisplayName("알림함·설정·구독은 직원 개인 계정만 쓴다 (매장 계정·본사는 403)")
    void should_forbidNonEmployees() throws Exception {
        api("store-gn01", get("/api/v1/notifications")).andExpect(status().isForbidden());
        api("hq", get("/api/v1/me/notification-settings")).andExpect(status().isForbidden());
        api("store-gn01", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUB))
                .andExpect(status().isForbidden());
    }

    // --- 알림 설정 ---

    @Test
    @DisplayName("알림 시점 기본은 전날(DAY_BEFORE)이고 바꿀 수 있다, 잘못된 값은 400")
    void should_changeTiming() throws Exception {
        api("gn01-junior", get("/api/v1/me/notification-settings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.timing").value("DAY_BEFORE")).andExpect(jsonPath("$.push").value(false));

        api("gn01-junior", put("/api/v1/me/notification-settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"timing\":\"BOTH\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.timing").value("BOTH"));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT notify_timing FROM staff WHERE login_id = 'gn01-junior'", String.class)).isEqualTo("BOTH");

        api("gn01-junior", put("/api/v1/me/notification-settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"timing\":\"WHENEVER\"}")).andExpect(status().isBadRequest());
        api("gn01-junior", put("/api/v1/me/notification-settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- 웹 푸시 구독 ---

    @Test
    @DisplayName("구독을 저장하면 push=true, 같은 endpoint를 다시 저장해도 한 행이고, 지우면 push=false (없는 구독 삭제도 204)")
    void should_subscribeAndUnsubscribe() throws Exception {
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUB))
                .andExpect(status().isNoContent());
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content(SUB.replace("\"pk\"", "\"new-key\""))).andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT p256dh FROM push_subscription", String.class)).isEqualTo("new-key");
        api("gn01-junior", get("/api/v1/me/notification-settings")).andExpect(jsonPath("$.push").value(true));

        // 같은 브라우저를 다른 직원이 쓰면 주인이 바뀐다
        api("gn01-senior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUB))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT staff_id FROM push_subscription", Long.class)).isEqualTo(id("gn01-senior"));

        api("gn01-junior", delete("/api/v1/me/push-subscriptions").param("endpoint", "https://push.example/abc"))
                .andExpect(status().isNoContent()); // 남의 구독은 안 지워진다
        em.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class)).isEqualTo(1);
        api("gn01-senior", delete("/api/v1/me/push-subscriptions").param("endpoint", "https://push.example/abc"))
                .andExpect(status().isNoContent());
        api("gn01-senior", delete("/api/v1/me/push-subscriptions").param("endpoint", "https://push.example/none"))
                .andExpect(status().isNoContent());
        api("gn01-senior", get("/api/v1/me/notification-settings")).andExpect(jsonPath("$.push").value(false));
    }

    @Test
    @DisplayName("구독 값이 비었거나 너무 길면 400")
    void should_validateSubscription() throws Exception {
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"\",\"keys\":{\"p256dh\":\"pk\",\"auth\":\"au\"}}")).andExpect(status().isBadRequest());
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://x\"}")).andExpect(status().isBadRequest());
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://x/" + "a".repeat(500) + "\",\"keys\":{\"p256dh\":\"pk\",\"auth\":\"au\"}}"))
                .andExpect(status().isBadRequest());
    }

    // --- 푸시 보내기 ---

    @Test
    @DisplayName("알림을 보내면 알림함에 쌓이고 그 직원의 모든 구독으로 푸시가 간다. 만료(GONE)된 구독은 지운다")
    void should_pushToAllSubscriptions_andDropGone() throws Exception {
        long junior = id("gn01-junior");
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUB));
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content(SUB.replace("abc", "gone")));
        em.flush();
        push.resultByEndpoint.put("https://push.example/gone", PushSender.Result.GONE);

        notifier.send(junior, NotificationType.FOLLOW_UP, 1L, 5L, "푸시 문구");
        em.flush();

        assertThat(push.messages).containsExactly("푸시 문구");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff_notification WHERE message = '푸시 문구'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("푸시가 실패해도 알림함에는 쌓이고, 같은 문구는 하루에 한 번만 보낸다 (sendOncePerDay)")
    void should_keepInbox_whenPushFails_andSendOncePerDay() throws Exception {
        long junior = id("gn01-junior");
        api("gn01-junior", post("/api/v1/me/push-subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUB));
        em.flush();
        push.resultByEndpoint.put("https://push.example/abc", PushSender.Result.FAILED);

        assertThat(notifier.sendOncePerDay(junior, NotificationType.FOLLOW_UP, 1L, 5L, "하루 한 번")).isTrue();
        assertThat(notifier.sendOncePerDay(junior, NotificationType.FOLLOW_UP, 1L, 5L, "하루 한 번")).isFalse();
        em.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff_notification WHERE message = '하루 한 번'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class)).isEqualTo(1); // FAILED는 지우지 않는다
    }

    @Test
    @DisplayName("비활성 직원에게는 알림을 쌓지 않는다")
    void should_skipInactiveStaff() throws Exception {
        long junior = id("gn01-junior");
        jdbc.update("UPDATE staff SET active = 0 WHERE id = ?", junior);
        em.clear();

        notifier.send(junior, NotificationType.NOTE, 1L, 5L, "안 가는 알림");
        em.flush();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM staff_notification", Integer.class)).isZero();
    }
}
