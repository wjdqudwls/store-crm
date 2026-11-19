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
import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.type.Target;
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
import java.time.Year;
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

        checkNames(request, nationality, errors);
        String phone = PhoneNumber.normalize(request.phone(), nationality);
        if (phone == null) {
            errors.add(new FieldError("phone", "전화번호 형식이 올바르지 않습니다."));
        }
        checkBirth(request, errors);
        checkSizes(request, errors);
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

    private static void checkNames(CustomerRegisterRequest request, Nationality nationality, List<FieldError> errors) {
        boolean hasName = request.name() != null && !request.name().isBlank();
        boolean hasNameEn = request.nameEn() != null && !request.nameEn().isBlank();
        if (nationality == Nationality.FOREIGN) {
            if (!hasNameEn) {
                errors.add(new FieldError("nameEn", "외국인은 영문 이름이 필요합니다."));
            }
        } else {
            if (!hasName) {
                errors.add(new FieldError("name", "이름이 필요합니다."));
            }
            if (hasNameEn) {
                errors.add(new FieldError("nameEn", "내국인은 영문 이름을 쓰지 않습니다."));
            }
        }
    }

    private static void checkBirth(CustomerRegisterRequest request, List<FieldError> errors) {
        Integer year = request.birthYear();
        if (year != null && (year < 1900 || year > Year.now(KstTime.ZONE).getValue())) {
            errors.add(new FieldError("birthYear", "태어난 해를 확인해 주세요."));
        }
        String md = request.birthMd();
        if (md != null && !md.isBlank() && !validMonthDay(md)) {
            errors.add(new FieldError("birthMd", "생일은 월일 4자리(MMDD)로 적어 주세요."));
        }
    }

    /** 0229는 윤년에만 있는 날짜지만 연도와 따로 받으므로 허용한다. */
    static boolean validMonthDay(String md) {
        if (!md.matches("\\d{4}")) {
            return false;
        }
        int month = Integer.parseInt(md.substring(0, 2));
        int day = Integer.parseInt(md.substring(2));
        if (month < 1 || month > 12 || day < 1) {
            return false;
        }
        return day <= java.time.Month.of(month).length(true);
    }

    private static void checkSizes(CustomerRegisterRequest request, List<FieldError> errors) {
        CustomerRegisterRequest.Sizes sizes = request.sizes();
        if (sizes == null) {
            return;
        }
        Target target = target(request.gender());
        checkSize("sizes.top", ItemType.TOP, target, sizes.top(), errors);
        checkSize("sizes.outer", ItemType.OUTER, target, sizes.outer(), errors);
        checkSize("sizes.bottom", ItemType.BOTTOM, target, sizes.bottom(), errors);
        checkSize("sizes.shoes", ItemType.SHOES, target, sizes.shoes(), errors);
    }

    /** 성별을 고르지 않으면 대상 없음: 그 품목의 모든 허용값 (02-erd.md 3.2). */
    private static Target target(Gender gender) {
        if (gender == null) {
            return null;
        }
        return gender == Gender.F ? Target.WOMEN : Target.MEN;
    }

    private static void checkSize(String field, ItemType item, Target target, String size, List<FieldError> errors) {
        if (size != null && !size.isBlank() && !SizeRule.isAllowed(item, target, size.trim())) {
            errors.add(new FieldError(field, "이 품목의 사이즈 목록에 없습니다."));
        }
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
        String detail = address == null ? null : blankToNull(address.detail());
        return CustomerDraft.builder()
                .name(request.name()).nameEn(request.nameEn()).nationality(nationality)
                .phoneEnc(cipher.encrypt(phone)).phoneHash(phoneHash).phoneLast4(PhoneNumber.last4(phone))
                .birthYear(request.birthYear() == null ? null : request.birthYear().shortValue())
                .birthMd(blankToNull(request.birthMd()))
                .regionSido(address == null ? null : blankToNull(address.sido()))
                .regionGu(address == null ? null : blankToNull(address.gu()))
                .addressDetailEnc(detail == null ? null : cipher.encrypt(detail))
                .gender(request.gender()).preferredItemTypes(request.preferredItemTypes())
                .sizeTop(sizes == null ? null : blankToNull(sizes.top()))
                .sizeOuter(sizes == null ? null : blankToNull(sizes.outer()))
                .sizeBottom(sizes == null ? null : blankToNull(sizes.bottom()))
                .sizeShoes(sizes == null ? null : blankToNull(sizes.shoes()))
                .contactChannel(request.contactChannel())
                .homeStoreId(account.storeId()).staffId(staff == null ? null : staff.getId())
                .source(CustomerSource.STORE)
                .privacyAgreed(true).age14Confirmed(true)
                .marketingSms(Boolean.TRUE.equals(request.agreements().marketingSms()))
                .marketingKakao(Boolean.TRUE.equals(request.agreements().marketingKakao()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
