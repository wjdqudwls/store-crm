package com.crmpos.common.service;

import com.crmpos.common.controller.MeResponse;
import com.crmpos.common.controller.TokenResponse;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.security.JwtService;
import com.crmpos.common.security.PasswordRules;
import com.crmpos.common.security.UnknownLoginAttempts;
import com.crmpos.common.web.KstTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·토큰 재발급·내 정보·비밀번호 바꾸기·모든 기기 로그아웃 (AUTH-01~04, ADR-025).
 * 로그에는 계정 ID만 남기고 로그인 ID·비밀번호는 남기지 않는다 (NFR-04b).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 없는 로그인 ID에도 같은 시간이 걸리게 비교하는 가짜 해시 (아이디 존재 여부를 응답 시간으로 알리지 않는다). */
    /** 연속 로그인 실패가 이만큼 쌓이면 잠근다 (총괄 결정 11/20). */
    static final int LOGIN_FAIL_LIMIT = 5;
    static final Duration LOGIN_LOCK = Duration.ofMinutes(10);

    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BbW3O6uV6xV3b8cK9q1m3Q4Zl8mK";

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UnknownLoginAttempts unknownAttempts;

    /**
     * 아이디·비밀번호가 맞으면 토큰을 준다. 틀리면 AUTH_INVALID_CREDENTIALS, 비활성이면 AUTH_ACCOUNT_DISABLED.
     * 같은 아이디가 5번 연속 틀리면 10분 동안 AUTH_LOGIN_LOCKED로 거절한다(맞는 비밀번호도). 없는 아이디도 같은 규칙으로
     * 세어서 응답으로 아이디가 있는지 알 수 없다. 실패 횟수가 롤백되지 않도록 예외에도 커밋한다(noRollbackFor).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(String loginId, String password) {
        String id = loginId == null ? "" : loginId.trim();
        Staff staff = staffRepository.findByLoginId(id).orElse(null);
        Instant now = Instant.now();
        LocalDateTime nowLocal = now();
        boolean locked = staff == null ? unknownAttempts.isLocked(id, now) : staff.isLoginLocked(nowLocal);
        // 없는 아이디·잠긴 계정에도 같은 비교를 해서 응답 시간으로 구별되지 않게 한다
        boolean matches = passwordEncoder.matches(password == null ? "" : password,
                staff == null ? DUMMY_HASH : staff.getPasswordHash());
        if (locked) {
            log.info("login rejected: locked");
            throw new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED);
        }
        if (staff == null || !matches) {
            recordFailure(staff, id, now, nowLocal);
            log.info("login failed");
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!staff.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (staff.getFailedLoginCount() > 0 || staff.getLockedUntil() != null) {
            staff.clearLoginFailures();
        }
        log.info("login ok staffId={}", staff.getId());
        return tokens(staff);
    }

    /** 실패를 세고, 5번째면 10분 잠근다. */
    private void recordFailure(Staff staff, String loginId, Instant now, LocalDateTime nowLocal) {
        if (staff == null) {
            unknownAttempts.recordFailure(loginId, now);
            return;
        }
        staffRepository.addLoginFailure(staff.getId());
        staffRepository.lockIfFailuresReached(staff.getId(), LOGIN_FAIL_LIMIT, nowLocal.plus(LOGIN_LOCK));
    }

    /** Refresh로 Access와 Refresh를 새로 받는다(14일 연장). 비활성·기준 시각 이전 토큰은 거절한다. */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        JwtService.ParsedToken parsed = jwtService.parseRefresh(refreshToken);
        Staff staff = staffRepository.findById(parsed.staffId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        if (!staff.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (parsed.issuedBefore(staff.getTokensValidAfter())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return tokens(staff);
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentAccount account) {
        return MeResponse.from(load(account));
    }

    /**
     * 비밀번호를 바꾼다(직원·본사). 현재 비밀번호가 틀리면 PASSWORD_MISMATCH, 규칙 위반이면 PASSWORD_POLICY_VIOLATION.
     * 성공하면 이 계정의 기존 로그인이 모두 끊기므로, 지금 기기가 계속 쓰도록 새 토큰을 돌려준다.
     */
    @Transactional
    public TokenResponse changePassword(CurrentAccount account, String currentPassword, String newPassword) {
        requirePersonalAccount(account);
        Staff staff = load(account);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, staff.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        PasswordRules.require(newPassword);
        staff.changePassword(passwordEncoder.encode(newPassword), now());
        log.info("password changed staffId={}", staff.getId());
        return tokens(staff);
    }

    /** 모든 기기에서 로그아웃(직원·본사). 지금 이전에 발급한 토큰이 모두 끊긴다. */
    @Transactional
    public void logoutAll(CurrentAccount account) {
        requirePersonalAccount(account);
        load(account).invalidateTokens(now());
        log.info("logout all staffId={}", account.id());
    }

    /** 비밀번호 바꾸기·모든 기기 로그아웃은 개인 계정(본사·직원)만. 매장 계정·키오스크는 본사가 비밀번호를 다시 정한다 (AUTH-04). */
    private static void requirePersonalAccount(CurrentAccount account) {
        account.requireRole(StaffRole.HQ, StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);
    }

    private Staff load(CurrentAccount account) {
        return staffRepository.findById(account.id()).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
    }

    private TokenResponse tokens(Staff staff) {
        return new TokenResponse(jwtService.issueAccess(staff), jwtService.issueRefresh(staff), "Bearer",
                (int) JwtService.ACCESS_TTL.toSeconds());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}
