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
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.controller.CustomerCreatedResponse;
import com.crmpos.customer.controller.CustomerRegisterRequest;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.domain.PhoneNumber;
import com.crmpos.customer.repository.CustomerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 직원 대리 등록 (REG-01·04·05·06). 입력을 검사하고, 전화번호·상세주소를 암호화하고, 같은 번호는 새로 만들지 않는다.
 * 요청 값(이름·번호·주소)은 로그에도 예외 메시지에도 남기지 않는다 (NFR-04b).
 */
@Service
@RequiredArgsConstructor
public class CustomerRegistrationService {

    private static final Set<StaffRole> STAFF_RANKS =
            Set.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private final CustomerRepository customerRepository;
    private final CustomerWriter customerWriter;
    private final StaffRepository staffRepository;
    private final AesGcmCipher cipher;
    private final HmacHasher hasher;

    /** 이 메서드는 트랜잭션 밖이다. 저장({@link CustomerWriter})이 끝난 뒤에 중복 오류를 다시 조회해 응답한다. */
    public CustomerCreatedResponse register(CustomerRegisterRequest request, CurrentAccount account) {
        List<FieldError> errors = new ArrayList<>();
        Nationality nationality = request.nationality() == null ? Nationality.DOMESTIC : request.nationality();

        CustomerInputRules.checkNames(request.name(), request.nameEn(), nationality, errors);
        String phone = PhoneNumber.normalize(request.phone(), nationality);
        if (phone == null) {
            errors.add(new FieldError("phone", "전화번호 형식이 올바르지 않습니다."));
        }
        CustomerInputRules.checkBirth(request.birthYear(), request.birthMd(), errors);
        CustomerRegisterRequest.Sizes sz = request.sizes();
        if (sz != null) {
            CustomerInputRules.checkSizes(request.gender(), sz.top(), sz.outer(), sz.bottom(), sz.shoes(), errors);
        }
        Staff staff = findStaff(request.staffId(), account, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String phoneHash = hasher.hash(phone);
        Customer existing = customerRepository.findByPhoneHash(phoneHash).orElse(null);
        if (existing != null) {
            throw duplicated(existing);
        }

        CustomerDraft draft = draft(request, nationality, phone, phoneHash, staff, account);
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE);
        try {
            Customer saved = customerWriter.insert(draft, now);
            return new CustomerCreatedResponse(saved.getId(), saved.getName(), PhoneNumber.masked(saved.getPhoneLast4()));
        } catch (DataIntegrityViolationException e) {
            // 같은 번호를 동시에 등록한 경우: UNIQUE가 막았으니 먼저 저장된 고객을 알려 준다
            Customer winner = customerRepository.findByPhoneHash(phoneHash).orElseThrow(() -> e);
            throw duplicated(winner);
        }
    }

    private static BusinessException duplicated(Customer existing) {
        return new BusinessException(ErrorCode.CUSTOMER_PHONE_DUPLICATED, Map.of(
                "customerId", existing.getId(),
                "name", existing.getName(),
                "phoneMasked", PhoneNumber.masked(existing.getPhoneLast4())));
    }

    private Staff findStaff(Long staffId, CurrentAccount account, List<FieldError> errors) {
        if (staffId == null) {
            return null;
        }
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        boolean sameStore = staff.getStore() != null && staff.getStore().getId().equals(account.storeId());
        if (!staff.isActive() || !sameStore || !STAFF_RANKS.contains(staff.getRole())) {
            errors.add(new FieldError("staffId", "이 매장의 활성 직원이 아닙니다."));
        }
        return staff;
    }

    private CustomerDraft draft(CustomerRegisterRequest request, Nationality nationality, String phone,
            String phoneHash, Staff staff, CurrentAccount account) {
        CustomerRegisterRequest.Address address = request.address();
        CustomerRegisterRequest.Sizes sizes = request.sizes();
        String detail = address == null ? null : CustomerInputRules.blankToNull(address.detail());
        return CustomerDraft.builder()
                .name(request.name()).nameEn(request.nameEn()).nationality(nationality)
                .phoneEnc(cipher.encrypt(phone)).phoneHash(phoneHash).phoneLast4(PhoneNumber.last4(phone))
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
                .homeStoreId(account.storeId()).staffId(staff == null ? null : staff.getId())
                .source(CustomerSource.STORE)
                .privacyAgreed(true).age14Confirmed(true)
                .marketingSms(Boolean.TRUE.equals(request.agreements().marketingSms()))
                .marketingKakao(Boolean.TRUE.equals(request.agreements().marketingKakao()))
                .build();
    }
}
