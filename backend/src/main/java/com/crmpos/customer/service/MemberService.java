package com.crmpos.customer.service;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.type.Target;
import com.crmpos.common.web.BodyValidation;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.controller.CustomerRegisterRequest;
import com.crmpos.customer.controller.MemberItem;
import com.crmpos.customer.controller.MemberRequest;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerMember;
import com.crmpos.customer.domain.MemberRelation;
import com.crmpos.customer.repository.CustomerMemberRepository;
import com.crmpos.customer.repository.CustomerRepository;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대표 고객 아래 가족 구성원 (CUS-05, BR-13, ADR-009). 조회는 키오스크를 뺀 로그인한 누구나, 추가·수정·삭제는 매장 계정만.
 * 호칭·사이즈는 로그에 남기지 않는다 (NFR-04b).
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final int FREE_SIZE_MAX = 20;
    private static final StaffRole[] CAN_READ = {StaffRole.HQ, StaffRole.STORE, StaffRole.MANAGER,
            StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF};

    private final CustomerRepository customerRepository;
    private final CustomerMemberRepository memberRepository;
    private final Validator validator;

    private record Values(String nickname, MemberRelation relation, Gender gender, Short birthYear, String top,
            String outer, String bottom, String shoes) {
    }

    @Transactional(readOnly = true)
    public List<MemberItem> list(Long customerId, CurrentAccount account) {
        account.requireRole(CAN_READ);
        findCustomer(customerId);
        return memberRepository.findByCustomerIdOrderByIdAsc(customerId).stream().map(MemberItem::from).toList();
    }

    @Transactional
    public MemberItem create(Long customerId, MemberRequest request, CurrentAccount account) {
        account.requireRole(StaffRole.STORE);
        Customer customer = findCustomer(customerId);
        Values v = check(request);
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        return MemberItem.from(memberRepository.save(CustomerMember.create(customer, v.nickname(), v.relation(),
                v.gender(), v.birthYear(), v.top(), v.outer(), v.bottom(), v.shoes(), now)));
    }

    @Transactional
    public MemberItem update(Long memberId, MemberRequest request, CurrentAccount account) {
        account.requireRole(StaffRole.STORE);
        CustomerMember member = findMember(memberId);
        Values v = check(request);
        member.change(v.nickname(), v.relation(), v.gender(), v.birthYear(), v.top(), v.outer(), v.bottom(), v.shoes());
        return MemberItem.from(member);
    }

    @Transactional
    public void delete(Long memberId, CurrentAccount account) {
        account.requireRole(StaffRole.STORE);
        memberRepository.delete(findMember(memberId));
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    private CustomerMember findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Values check(MemberRequest request) {
        List<FieldError> errors = new ArrayList<>(BodyValidation.violations(validator, request));
        Integer year = request.birthYear();
        if (year != null && (year < 1900 || year > LocalDate.now(KstTime.ZONE).getYear())) {
            errors.add(new FieldError("birthYear", "출생연도를 확인해 주세요."));
        }
        CustomerRegisterRequest.Sizes sizes = request.sizes();
        String top = sizes == null ? null : CustomerInputRules.blankToNull(sizes.top());
        String outer = sizes == null ? null : CustomerInputRules.blankToNull(sizes.outer());
        String bottom = sizes == null ? null : CustomerInputRules.blankToNull(sizes.bottom());
        String shoes = sizes == null ? null : CustomerInputRules.blankToNull(sizes.shoes());
        boolean child = request.relation() == MemberRelation.CHILD;
        Target target = request.gender() == null ? null : request.gender() == Gender.F ? Target.WOMEN : Target.MEN;
        checkSize("sizes.top", ItemType.TOP, target, top, child, errors);
        checkSize("sizes.outer", ItemType.OUTER, target, outer, child, errors);
        checkSize("sizes.bottom", ItemType.BOTTOM, target, bottom, child, errors);
        checkSize("sizes.shoes", ItemType.SHOES, target, shoes, child, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new Values(request.nickname().trim(), request.relation(), request.gender(),
                year == null ? null : year.shortValue(), top, outer, bottom, shoes);
    }

    /**
     * 자녀는 직접 입력도 되므로 20자까지 아무 값이나 받는다 (05 U14, 2026-11-27 사용자 결정). 그 밖의 관계는 성별에 맞는
     * 허용값만 받는다(고객과 같은 규칙, 02-erd.md 3.2).
     */
    private static void checkSize(String field, ItemType item, Target target, String size, boolean child,
            List<FieldError> errors) {
        if (size == null) {
            return;
        }
        if (child) {
            if (size.length() > FREE_SIZE_MAX) {
                errors.add(new FieldError(field, FREE_SIZE_MAX + "자까지 쓸 수 있습니다."));
            }
        } else if (!SizeRule.isAllowed(item, target, size)) {
            errors.add(new FieldError(field, "이 품목의 사이즈 목록에 없습니다."));
        }
    }
}
