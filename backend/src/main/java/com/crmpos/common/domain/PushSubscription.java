package com.crmpos.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 직원 브라우저의 웹 푸시 구독 (CAL-08, ADR-035). 한 직원이 폰·PC 여러 개를 켤 수 있어 직원당 여러 행이다.
 * endpoint·키는 구독자만 쓰는 비밀에 가까워 toString에 담지 않는다 (NFR-04b).
 */
@Entity
@Table(name = "push_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static PushSubscription of(Staff staff, String endpoint, String p256dh, String auth, LocalDateTime now) {
        PushSubscription s = new PushSubscription();
        s.staff = staff;
        s.endpoint = endpoint;
        s.p256dh = p256dh;
        s.auth = auth;
        s.createdAt = now;
        return s;
    }

    /** 같은 브라우저가 다시 구독하면 주인과 키를 새 값으로 바꾼다 (다른 직원이 같은 브라우저를 쓰는 경우도 포함). */
    public void renew(Staff newStaff, String newP256dh, String newAuth) {
        this.staff = newStaff;
        this.p256dh = newP256dh;
        this.auth = newAuth;
    }

    @Override
    public String toString() {
        return "PushSubscription[id=" + id + "]";
    }
}
