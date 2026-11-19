package com.crmpos.customer.controller;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.service.CustomerQueryService;
import com.crmpos.customer.service.CustomerRegistrationService;
import com.crmpos.customer.service.CustomerUpdateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 고객 등록·검색·상세·수정 (REG-04, CUS-01·02·04). 조회는 키오스크를 뺀 로그인한 누구나, 등록은 매장, 수정은 매장·본사. */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private static final StaffRole[] CAN_READ = {StaffRole.HQ, StaffRole.STORE, StaffRole.MANAGER,
            StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF};

    private final CustomerRegistrationService registrationService;
    private final CustomerQueryService queryService;
    private final CustomerUpdateService updateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerCreatedResponse register(CurrentAccount account, @Valid @RequestBody CustomerRegisterRequest request) {
        account.requireRole(StaffRole.STORE);
        return registrationService.register(request, account);
    }

    @GetMapping
    public PageResponse<CustomerListItem> search(CurrentAccount account,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        account.requireRole(CAN_READ);
        return queryService.search(name, page, size);
    }

    @GetMapping("/{id}")
    public CustomerDetailResponse get(CurrentAccount account, @PathVariable Long id,
            @RequestParam(required = false) String include) {
        account.requireRole(CAN_READ);
        return queryService.get(id, "address".equals(include));
    }

    @PatchMapping("/{id}")
    public CustomerDetailResponse update(CurrentAccount account, @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        return updateService.update(id, request, account);
    }
}
