package com.crmpos.common.security;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT를 만들고 읽는다 (AUTH-01, ADR-025). Access 30분, Refresh 14일. Refresh는 서버에 저장하지 않고,
 * 재발급할 때 새 Refresh를 함께 줘서 매일 쓰는 기기는 계속 로그인 상태로 둔다.
 * 토큰에는 계정 ID와 종류, 발급 시각(밀리초)만 담는다. 계정 상태·역할은 요청마다 DB에서 읽는다.
 */
@Component
public class JwtService {

    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_ISSUED_MS = "iatMs";
    static final String ACCESS = "access";
    static final String REFRESH = "refresh";

    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${crm.jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    JwtService(String secret, Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 한다");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.clock = clock;
    }

    public String issueAccess(Staff staff) {
        return issueAccess(staff.getId());
    }

    public String issueAccess(Long staffId) {
        return issue(staffId, ACCESS, ACCESS_TTL);
    }

    public String issueRefresh(Staff staff) {
        return issue(staff.getId(), REFRESH, REFRESH_TTL);
    }

    private String issue(Long staffId, String type, Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder().subject(String.valueOf(staffId)).claim(CLAIM_TYPE, type)
                .claim(CLAIM_ISSUED_MS, now.toEpochMilli()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }

    public ParsedToken parseAccess(String token) {
        return parse(token, ACCESS);
    }

    public ParsedToken parseRefresh(String token) {
        return parse(token, REFRESH);
    }

    /** 서명·만료·종류를 확인한다. 만료는 AUTH_TOKEN_EXPIRED, 나머지 문제는 AUTH_TOKEN_INVALID. */
    private ParsedToken parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                    .parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
            }
            // JSON 숫자는 구현에 따라 Integer·Long·Double로 읽히므로 Number로 받는다
            if (!(claims.get(CLAIM_ISSUED_MS) instanceof Number issuedMs)) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
            }
            return new ParsedToken(Long.valueOf(claims.getSubject()), Instant.ofEpochMilli(issuedMs.longValue()));
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    public record ParsedToken(Long staffId, Instant issuedAt) {

        /** 로그인 기준 시각(tokens_valid_after)보다 먼저 발급됐는가. 토큰 시각이 밀리초라 기준 시각도 밀리초로 맞춰 비교한다. */
        public boolean issuedBefore(java.time.LocalDateTime validAfter) {
            long validAfterMs = validAfter.atZone(com.crmpos.common.web.KstTime.ZONE).toInstant().toEpochMilli();
            return issuedAt.toEpochMilli() < validAfterMs;
        }
    }
}
