package com.crmpos.common.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 로그인·내 정보 (AUTH-01~04, 03-api.md 3.1). */
@Tag(name = "로그인·내 정보")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "공개. 아이디·비밀번호가 맞으면 Access(30분)와 Refresh(14일)를 준다. 틀리면 AUTH_INVALID_CREDENTIALS 401, 비활성 계정은 AUTH_ACCOUNT_DISABLED 401.")
    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.loginId(), request.password());
    }

    @Operation(summary = "토큰 재발급", description = "공개. Refresh로 Access와 Refresh를 새로 받는다(14일 연장). 만료는 AUTH_TOKEN_EXPIRED, 위조·로그아웃 이전 발급은 AUTH_TOKEN_INVALID, 비활성은 AUTH_ACCOUNT_DISABLED (모두 401).")
    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "내 정보", description = "로그인한 모든 계정(KIOSK 포함). 이름·역할·매니저 자리·소속 매장.")
    @GetMapping("/me")
    public MeResponse me(CurrentAccount account) {
        return authService.me(account);
    }

    @Operation(summary = "비밀번호 바꾸기", description = "본사·직원 개인 계정만(그 외 ACCESS_DENIED 403). 현재 비밀번호가 틀리면 PASSWORD_MISMATCH 400, 규칙(8자 이상·영문+숫자) 위반은 PASSWORD_POLICY_VIOLATION 400. 성공하면 다른 기기의 로그인이 끊기고, 지금 기기가 쓸 새 토큰을 돌려준다.")
    @PatchMapping("/me/password")
    public TokenResponse changePassword(CurrentAccount account, @Valid @RequestBody PasswordChangeRequest request) {
        return authService.changePassword(account, request.currentPassword(), request.newPassword());
    }

    @Operation(summary = "모든 기기에서 로그아웃", description = "본사·직원 개인 계정만. 지금 이전에 발급한 모든 토큰이 끊긴다.")
    @PostMapping("/auth/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(CurrentAccount account) {
        authService.logoutAll(account);
    }
}
