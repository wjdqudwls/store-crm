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
import jakarta.validation.Validator;
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
    private final Validator validator;

    /** 이 메서드는 트랜잭션 밖이다. 저장({@link CustomerWriter})이 끝난 뒤에 중복 오류를 다시 조회해 응답한다. */
    public CustomerCreatedResponse register(CustomerRegisterRequest request, CurrentAccount account) {
        Customer saved = validateAndInsert(request, errors -> findStaff(request.staffId(), account, errors),
                account.storeId(), CustomerSource.STORE);
        return new CustomerCreatedResponse(saved.getId(), saved.getName(), PhoneNumber.masked(saved.getPhoneLast4()));
    }

    /**
     * 고객이 직접 하는 등록(QR·키오스크, REG-01·02·03·05). 가입매장과 담당 직원(없으면 null)은 부르는 쪽이 정한다.
     * 같은 번호가 이미 있으면 아무것도 바꾸지 않고 정상으로 끝낸다 (가입 여부를 드러내지 않는다, REG-05).
     * 형식 오류는 직원 등록과 같이 fieldErrors로 모아 400이다.
     */
    public void registerBySelf(CustomerRegisterRequest request, Long homeStoreId, Staff staff, CustomerSource source) {
        try {
            validateAndInsert(request, errors -> staff, homeStoreId, source);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.CUSTOMER_PHONE_DUPLICATED) {
                throw e;
            }
        }
    }

    private Customer validateAndInsert(CustomerRegisterRequest request,
            java.util.function.Function<List<FieldError>, Staff> staffResolver, Long homeStoreId,
            CustomerSource source) {
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
        Staff staff = staffResolver.apply(errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String phoneHash = hasher.hash(phone);
        Customer existing = customerRepository.findByPhoneHash(phoneHash).orElse(null);
        if (existing != null) {
            throw duplicated(existing);
        }

        CustomerDraft draft = draft(request, nationality, phone, phoneHash, staff, homeStoreId, source);
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE);
        try {
            return customerWriter.insert(draft, now);
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
            String phoneHash, Staff staff, Long homeStoreId, CustomerSource source) {
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
                .homeStoreId(homeStoreId).staffId(staff == null ? null : staff.getId())
                .source(source)
                .privacyAgreed(true).age14Confirmed(true)
                .marketingSms(Boolean.TRUE.equals(request.agreements().marketingSms()))
                .marketingKakao(Boolean.TRUE.equals(request.agreements().marketingKakao()))
                .build();
    }
}
