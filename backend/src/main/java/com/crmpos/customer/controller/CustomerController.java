package com.crmpos.customer.controller;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.customer.service.CustomerRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 고객 등록·조회 (REG, CUS). 이 PR은 직원 대리 등록(매장 계정)만 연다. */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRegistrationService registrationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerCreatedResponse register(CurrentAccount account, @Valid @RequestBody CustomerRegisterRequest request) {
        account.requireRole(StaffRole.STORE);
        return registrationService.register(request, account);
    }
}
