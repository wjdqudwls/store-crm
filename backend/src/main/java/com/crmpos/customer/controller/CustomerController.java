package com.crmpos.customer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.service.CustomerQueryService;
import com.crmpos.customer.service.CustomerRegistrationService;
import com.crmpos.customer.service.CustomerUpdateService;
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

/**
 * 고객 등록·검색·상세·수정 (REG-04, CUS-01·02·04). 조회는 키오스크를 뺀 로그인한 누구나, 등록은 매장, 수정은 매장·본사.
 * 등록·수정 본문은 {@code @Valid}를 쓰지 않는다: 서비스가 형식 오류와 업무 검사 오류를 한 번에 모아 400으로 돌려준다.
 */
@Tag(name = "고객")
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private static final StaffRole[] CAN_READ = {StaffRole.HQ, StaffRole.STORE, StaffRole.MANAGER,
            StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF};

    private final CustomerRegistrationService registrationService;
    private final CustomerQueryService queryService;
    private final CustomerUpdateService updateService;

    @Operation(summary = "고객 등록(직원 대리)", description = "매장 계정·본사가 고객을 등록한다. 형식·업무 오류는 한 응답의 fieldErrors로 모아 400, 같은 전화번호는 CUSTOMER_PHONE_DUPLICATED 409(기존 고객이 details에). 성공하면 201.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerCreatedResponse register(CurrentAccount account, @RequestBody CustomerRegisterRequest request) {
        account.requireRole(StaffRole.STORE);
        return registrationService.register(request, account);
    }

    @Operation(summary = "고객 검색", description = "이름(부분 일치) 등으로 찾는다. 목록에는 전화번호 뒷자리 4개만 나온다. 로그인한 모든 계정이 쓴다.")
    @GetMapping
    public PageResponse<CustomerListItem> search(CurrentAccount account,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        account.requireRole(CAN_READ);
        return queryService.search(name, page, size);
    }

    @Operation(summary = "고객 상세", description = "요약·프로필·등급·가족 구성원. 상세주소는 include=address를 보낼 때만 준다(BR-08). 없으면 CUSTOMER_NOT_FOUND 404.")
    @GetMapping("/{id}")
    public CustomerDetailResponse get(CurrentAccount account, @PathVariable Long id,
            @RequestParam(required = false) String include) {
        account.requireRole(CAN_READ);
        return queryService.get(id, "address".equals(include));
    }

    @Operation(summary = "고객 수정", description = "매장 계정·본사가 프로필·담당 직원·마케팅 수신을 바꾼다. 오류는 등록과 같이 fieldErrors로 모아 400. 없으면 CUSTOMER_NOT_FOUND 404.")
    @PatchMapping("/{id}")
    public CustomerDetailResponse update(CurrentAccount account, @PathVariable Long id,
            @RequestBody CustomerUpdateRequest request) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        return updateService.update(id, request, account);
    }
}
