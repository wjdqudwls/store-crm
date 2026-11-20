package com.crmpos.common.controller;

import java.util.List;

/** 내 알림함 응답: 안 읽은 개수(배지)와 목록 (03-api.md 4.12). */
public record NotificationInbox(long unreadCount, List<NotificationItem> content, int page, int size,
        long totalElements, int totalPages) {
}
