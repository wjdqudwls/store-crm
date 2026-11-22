package com.crmpos.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 웹 푸시 보내기 구현을 고른다 (ADR-035). VAPID 키(VAPID_PUBLIC_KEY·VAPID_PRIVATE_KEY)가 둘 다 있으면 실제로 보내고,
 * 없으면 알림함에만 쌓는다. 키는 환경변수로만 받는다 (NFR-06).
 */
@Configuration
public class PushConfig {

    @Bean
    PushSender pushSender(@Value("${crm.push.vapid-public-key:}") String publicKey,
            @Value("${crm.push.vapid-private-key:}") String privateKey) {
        return new NoPushSender();
    }
}
