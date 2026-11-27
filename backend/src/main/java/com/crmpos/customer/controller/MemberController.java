package com.crmpos.customer.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.customer.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가족 구성원 (CUS-05). 본문은 {@code @Valid}를 쓰지 않고 서비스가 형식·업무 오류를 모아 400으로 준다. */
@Tag(name = "가족 구성원")
@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService service;

    @Operation(summary = "가족 구성원 목록", description = "키오스크를 뺀 로그인한 누구나. 등록 순서. 없는 고객은 CUSTOMER_NOT_FOUND 404.")
    @GetMapping("/customers/{customerId}/members")
    public List<MemberItem> list(CurrentAccount account, @PathVariable Long customerId) {
        return service.list(customerId, account);
    }

    @Operation(summary = "가족 구성원 추가", description = "매장 계정만. 호칭·관계 필수, 성별·출생연도·사이즈 선택. 자녀(CHILD) 사이즈는 20자까지 직접 입력 가능, 그 밖에는 허용값만. 성공하면 201, 오류는 fieldErrors와 함께 400.")
    @PostMapping("/customers/{customerId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberItem create(CurrentAccount account, @PathVariable Long customerId,
            @RequestBody MemberRequest request) {
        return service.create(customerId, request, account);
    }

    @Operation(summary = "가족 구성원 수정", description = "매장 계정만. 본문 전체로 바꾼다(보내지 않은 선택 칸은 비움). 없으면 MEMBER_NOT_FOUND 404.")
    @PatchMapping("/members/{memberId}")
    public MemberItem update(CurrentAccount account, @PathVariable Long memberId, @RequestBody MemberRequest request) {
        return service.update(memberId, request, account);
    }

    @Operation(summary = "가족 구성원 삭제", description = "매장 계정만. 과거 판매의 착용자 호칭은 남는다. 없으면 MEMBER_NOT_FOUND 404. 성공하면 204.")
    @DeleteMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentAccount account, @PathVariable Long memberId) {
        service.delete(memberId, account);
    }
}
