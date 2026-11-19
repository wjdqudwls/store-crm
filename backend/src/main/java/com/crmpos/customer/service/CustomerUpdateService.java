package com.crmpos.customer.service;

import com.crmpos.common.crypto.AesGcmCipher;
import com.crmpos.common.crypto.HmacHasher;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.BodyValidation;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.controller.CustomerDetailResponse;
import com.crmpos.customer.controller.CustomerRegisterRequest;
import com.crmpos.customer.controller.CustomerUpdateRequest;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.domain.PhoneNumber;
import com.crmpos.customer.repository.CustomerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 고객 프로필 수정 (CUS-04). 등록과 같은 입력 검사를 쓰고, 번호를 바꿨는데 다른 고객의 번호면 바꾸지 않고 그 고객을 알려 준다
 * (BR-01, REG-05). 요청 값(이름·번호·주소)은 로그에도 예외 메시지에도 남기지 않는다 (NFR-04b).
 */
@Service
@RequiredArgsConstructor
public class CustomerUpdateService {

    private static final Set<StaffRole> STAFF_RANKS =
            Set.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private final CustomerRepository customerRepository;
    private final CustomerWriter customerWriter;
    private final CustomerQueryService queryService;
    private final StaffRepository staffRepository;
    private final AesGcmCipher cipher;
    private final HmacHasher hasher;
    private final Validator validator;

    /** 이 메서드는 트랜잭션 밖이다. 저장({@link CustomerWriter})이 끝난 뒤 중복 오류를 다시 조회해 응답한다. */
    public CustomerDetailResponse update(Long id, CustomerUpdateRequest request, CurrentAccount account) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        // 형식 오류(Bean Validation)와 업무 검사 오류를 한 번에 모아서 돌려준다
        List<FieldError> errors = new ArrayList<>(BodyValidation.violations(validator, request));
        Nationality nationality = request.nationality() == null ? Nationality.DOMESTIC : request.nationality();
        CustomerInputRules.checkNames(request.name(), request.nameEn(), nationality, errors);
        String phone = PhoneNumber.normalize(request.phone(), nationality);
        if (phone == null) {
            BodyValidation.addIfAbsent(errors, new FieldError("phone", "전화번호 형식이 올바르지 않습니다."));
        }
        CustomerInputRules.checkBirth(request.birthYear(), request.birthMd(), errors);
        CustomerRegisterRequest.Sizes sz = request.sizes();
        if (sz != null) {
            CustomerInputRules.checkSizes(request.gender(), sz.top(), sz.outer(), sz.bottom(), sz.shoes(), errors);
        }
        Long storeForStaff = account.storeId() != null ? account.storeId() : customer.getHomeStoreId();
        Staff staff = findStaff(request.staffId(), storeForStaff, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String phoneHash = hasher.hash(phone);
        boolean phoneChanged = !phoneHash.equals(customer.getPhoneHash());
        if (phoneChanged) {
            Customer other = customerRepository.findByPhoneHash(phoneHash).orElse(null);
            if (other != null && !other.getId().equals(id)) {
                throw duplicated(other);
            }
        }

        CustomerDraft draft = draft(request, nationality, phone, phoneHash, phoneChanged ? null : customer, staff);
        try {
            customerWriter.update(id, draft, LocalDateTime.now(KstTime.ZONE));
        } catch (DataIntegrityViolationException e) {
            Customer winner = customerRepository.findByPhoneHash(phoneHash).orElseThrow(() -> e);
            throw duplicated(winner);
        }
        return queryService.get(id, false);
    }

    private static BusinessException duplicated(Customer other) {
        return new BusinessException(ErrorCode.CUSTOMER_PHONE_DUPLICATED, Map.of(
                "customerId", other.getId(),
                "name", other.getName(),
                "phoneMasked", PhoneNumber.masked(other.getPhoneLast4())));
    }

    private Staff findStaff(Long staffId, Long storeId, List<FieldError> errors) {
        if (staffId == null) {
            return null;
        }
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        boolean sameStore = staff.getStore() != null && staff.getStore().getId().equals(storeId);
        if (!staff.isActive() || !sameStore || !STAFF_RANKS.contains(staff.getRole())) {
            errors.add(new FieldError("staffId", "이 매장의 활성 직원이 아닙니다."));
        }
        return staff;
    }

    /** 번호가 그대로면 이미 저장된 암호문을 다시 쓴다(불필요한 재암호화를 피한다). */
    private CustomerDraft draft(CustomerUpdateRequest request, Nationality nationality, String phone,
            String phoneHash, Customer unchangedPhoneOf, Staff staff) {
        CustomerRegisterRequest.Address address = request.address();
        CustomerRegisterRequest.Sizes sizes = request.sizes();
        String detail = address == null ? null : CustomerInputRules.blankToNull(address.detail());
        byte[] phoneEnc = unchangedPhoneOf != null ? unchangedPhoneOf.getPhoneEnc() : cipher.encrypt(phone);
        CustomerUpdateRequest.Marketing marketing = request.marketing();
        return CustomerDraft.builder()
                .name(request.name()).nameEn(request.nameEn()).nationality(nationality)
                .phoneEnc(phoneEnc).phoneHash(phoneHash).phoneLast4(PhoneNumber.last4(phone))
                .birthYear(request.birthYear() == null ? null : request.birthYear().shortValue())
                .birthMd(CustomerInputRules.blankToNull(request.birthMd()))
                .regionSido(address == null ? null : CustomerInputRules.blankToNull(address.sido()))
                .regionGu(address == null ? null : CustomerInputRules.blankToNull(address.gu()))
                .addressDetailEnc(detail == null ? null : cipher.encrypt(detail))
                .gender(request.gender()).preferredItemTypes(request.preferredItemTypes())
                .sizeTop(sizes == null ? null : CustomerInputRules.blankToNull(sizes.top()))
                .sizeOuter(sizes == null ? null : CustomerInputRules.blankToNull(sizes.outer()))
                .sizeBottom(sizes == null ? null : CustomerInputRules.blankToNull(sizes.bottom()))
                .sizeShoes(sizes == null ? null : CustomerInputRules.blankToNull(sizes.shoes()))
                .contactChannel(request.contactChannel())
                .staffId(staff == null ? null : staff.getId())
                .marketingSms(Boolean.TRUE.equals(marketing.sms()))
                .marketingKakao(Boolean.TRUE.equals(marketing.kakao()))
                .build();
    }
}
