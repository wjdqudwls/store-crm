package com.crmpos.customer.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.service.FollowUpRuleService;
import com.crmpos.customer.service.FollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 후속 연락 (CAL-06·07·10, BR-14, 03-api.md 3.3·3.6·4.7). */
@Tag(name = "후속 연락")
@RestController
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpService followUpService;
    private final FollowUpRuleService ruleService;

    @Operation(summary = "오늘 할 일", description = "만기일이 오늘이거나 지난 열린 할 일(오래된 순). 직원은 내 할 일, 매장 계정은 매장 전체(매장 계정이 맡은 고객 포함), 본사는 storeId 필수(없으면 VALIDATION_FAILED 400, 없는 매장 STORE_NOT_FOUND 404).")
    @GetMapping("/follow-ups/today")
    public PageResponse<FollowUpTodayItem> today(CurrentAccount account, @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return followUpService.today(account, storeId, page, size);
    }

    @Operation(summary = "고객의 후속 연락 할 일", description = "열린 것 먼저, 만기일 순. 로그인한 모든 계정(KIOSK 제외). 없는 고객은 CUSTOMER_NOT_FOUND 404.")
    @GetMapping("/customers/{customerId}/follow-ups")
    public PageResponse<FollowUpItem> ofCustomer(@PathVariable Long customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return followUpService.ofCustomer(customerId, page, size);
    }

    @Operation(summary = "연락 결과로 완료", description = "연락 메모(CALL)를 만들고 할 일을 완료한다. 매장 계정은 staffId(연락한 직원) 필수, 직원은 내 할 일만(남의 할 일 ACCESS_DENIED 403). 없는 할 일 404, 이미 닫힘 FOLLOW_UP_ALREADY_CLOSED 409, 결과·채널 누락 400.")
    @PostMapping("/follow-ups/{id}/complete")
    public FollowUpItem complete(CurrentAccount account, @PathVariable Long id,
            @RequestBody FollowUpCompleteRequest request) {
        return followUpService.complete(id, request, account);
    }

    @Operation(summary = "건너뛰기", description = "기록 없이 할 일을 끝낸다. 직원은 내 할 일만(403). 없는 할 일 404, 이미 닫힘 409.")
    @PostMapping("/follow-ups/{id}/skip")
    public FollowUpItem skip(CurrentAccount account, @PathVariable Long id) {
        return followUpService.skip(id, account);
    }

    @Operation(summary = "방문함으로 완료", description = "방문 예약(VISIT) 할 일을 방문함으로 완료한다. 방문 예약이 아니면 VALIDATION_FAILED 400. 권한·오류는 건너뛰기와 같다.")
    @PostMapping("/follow-ups/{id}/visited")
    public FollowUpItem visited(CurrentAccount account, @PathVariable Long id) {
        return followUpService.visited(id, account);
    }

    @Operation(summary = "후속 연락 단계 설정 조회", description = "단계 이름·기간·홍보 여부·켜짐. 로그인한 모든 계정(KIOSK 제외).")
    @GetMapping("/follow-up-rules")
    public List<FollowUpRuleItem> rules() {
        return ruleService.list();
    }

    @Operation(summary = "후속 연락 단계 설정 저장", description = "본사만(그 외 ACCESS_DENIED 403). 전체 저장: id가 있으면 고치고 없으면 새 단계, 목록에 없는 기존 단계는 꺼진다(지우지 않음). 이후 구매부터 적용되고 이미 만든 할 일은 그대로. 오류는 fieldErrors로 모아 400.")
    @PutMapping("/follow-up-rules")
    public List<FollowUpRuleItem> saveRules(CurrentAccount account, @RequestBody List<FollowUpRuleRequest> request) {
        return ruleService.save(request, account);
    }
}
