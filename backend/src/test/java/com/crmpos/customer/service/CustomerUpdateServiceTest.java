package com.crmpos.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crmpos.common.crypto.AesGcmCipher;
import com.crmpos.common.crypto.HmacHasher;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.customer.controller.CustomerDetailResponse;
import com.crmpos.customer.controller.CustomerRegisterRequest;
import com.crmpos.customer.controller.CustomerUpdateRequest;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.repository.CustomerRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerUpdateServiceTest {

    private static final String CRYPTO_KEY = "dGVzdC1vbmx5LWNyeXB0by1rZXktMzItYnl0ZXMhISE=";
    private static final String HMAC_KEY = "dGVzdC1vbmx5LWhtYWMta2V5LTMyLWJ5dGVzLW1vcmU=";
    private static final CurrentAccount STORE = new CurrentAccount(2L, StaffRole.STORE, 3L);
    private static final CurrentAccount HQ = new CurrentAccount(1L, StaffRole.HQ, null);

    @Mock
    CustomerRepository customerRepository;
    @Mock
    CustomerWriter customerWriter;
    @Mock
    CustomerQueryService queryService;
    @Mock
    StaffRepository staffRepository;

    final AesGcmCipher cipher = new AesGcmCipher(CRYPTO_KEY);
    final HmacHasher hasher = new HmacHasher(HMAC_KEY);
    CustomerUpdateService service;
    Customer existing;

    @BeforeEach
    void setUp() {
        service = new CustomerUpdateService(customerRepository, customerWriter, queryService, staffRepository,
                cipher, hasher, jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        existing = customer(1024L, "김하늘", "+821029434821", 3L);
        lenient().when(customerRepository.findById(1024L)).thenReturn(Optional.of(existing));
        lenient().when(queryService.get(1024L, false)).thenReturn(mock(CustomerDetailResponse.class));
    }

    private Customer customer(long id, String name, String phone, Long storeId) {
        CustomerDraft draft = CustomerDraft.builder().name(name).nationality(Nationality.DOMESTIC)
                .phoneEnc(cipher.encrypt(phone)).phoneHash(hasher.hash(phone))
                .phoneLast4(phone.substring(phone.length() - 4)).privacyAgreed(true).age14Confirmed(true)
                .homeStoreId(storeId).source(CustomerSource.STORE).build();
        Customer customer = Customer.register(draft, LocalDateTime.of(2026, 11, 19, 10, 0));
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private static CustomerUpdateRequest request(String name, String phone, Long staffId) {
        return new CustomerUpdateRequest(name, null, phone, Nationality.DOMESTIC, null, null, null, null, null, null,
                null, staffId, new CustomerUpdateRequest.Marketing(true, false));
    }

    private static Staff staff(long id, StaffRole role, Long storeId, boolean active) {
        Staff staff = mock(Staff.class);
        lenient().when(staff.getId()).thenReturn(id);
        lenient().when(staff.getRole()).thenReturn(role);
        lenient().when(staff.isActive()).thenReturn(active);
        Store store = mock(Store.class);
        lenient().when(store.getId()).thenReturn(storeId);
        lenient().when(staff.getStore()).thenReturn(store);
        return staff;
    }

    @Test
    @DisplayName("없는 고객이면 404 CUSTOMER_NOT_FOUND")
    void should_throw404_when_customerMissing() {
        given(customerRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("김하늘", "010-2943-4821", null), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    @Test
    @DisplayName("번호를 그대로 두면 중복 확인 없이 이미 저장된 암호문을 다시 쓴다")
    void should_reuseEncryptedPhone_when_phoneUnchanged() {
        service.update(1024L, request("김하나", "+82 10 2943 4821", null), STORE);

        ArgumentCaptor<CustomerDraft> captor = ArgumentCaptor.forClass(CustomerDraft.class);
        verify(customerWriter).update(eq(1024L), captor.capture(), any());
        assertThat(captor.getValue().phoneEnc()).isSameAs(existing.getPhoneEnc());
        assertThat(captor.getValue().name()).isEqualTo("김하나");
        verify(customerRepository, never()).findByPhoneHash(any());
    }

    @Test
    @DisplayName("번호를 바꾸면 새 번호로 암호화·해시하고 뒷자리도 바뀐다")
    void should_encryptNewPhone_when_phoneChanged() {
        given(customerRepository.findByPhoneHash(hasher.hash("+821099990937"))).willReturn(Optional.empty());

        service.update(1024L, request("김하늘", "010-9999-0937", null), STORE);

        ArgumentCaptor<CustomerDraft> captor = ArgumentCaptor.forClass(CustomerDraft.class);
        verify(customerWriter).update(eq(1024L), captor.capture(), any());
        assertThat(cipher.decrypt(captor.getValue().phoneEnc())).isEqualTo("+821099990937");
        assertThat(captor.getValue().phoneLast4()).isEqualTo("0937");
        assertThat(captor.getValue().phoneHash()).isEqualTo(hasher.hash("+821099990937"));
    }

    @Test
    @DisplayName("바꾼 번호가 다른 고객 것이면 바꾸지 않고 409에 그 고객을 알려 준다")
    void should_throw409_when_phoneBelongsToOtherCustomer() {
        Customer other = customer(2310L, "박도윤", "+821011110937", 3L);
        given(customerRepository.findByPhoneHash(hasher.hash("+821011110937"))).willReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1024L, request("김하늘", "010-1111-0937", null), STORE))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_PHONE_DUPLICATED);
                    assertThat(e.getDetails()).isEqualTo(Map.of("customerId", 2310L, "name", "박도윤",
                            "phoneMasked", "****-0937"));
                });
        verify(customerWriter, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("저장 중 UNIQUE에 걸려도 먼저 그 번호를 가진 고객으로 409를 준다")
    void should_throw409_when_uniqueViolationOnUpdate() {
        Customer winner = customer(2310L, "박도윤", "+821011110937", 3L);
        given(customerRepository.findByPhoneHash(hasher.hash("+821011110937")))
                .willReturn(Optional.empty()).willReturn(Optional.of(winner));
        given(customerWriter.update(any(), any(), any())).willThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.update(1024L, request("김하늘", "010-1111-0937", null), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_PHONE_DUPLICATED));
    }

    @Test
    @DisplayName("전화번호 형식이 틀리면 400 fieldErrors[phone]")
    void should_returnPhoneFieldError_when_invalid() {
        assertThatThrownBy(() -> service.update(1024L, request("김하늘", "abc", null), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("phone"));
        verify(customerWriter, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("담당 직원은 그 매장의 활성 직원이어야 하고, 본사 계정은 고객의 가입매장 기준이다")
    void should_checkStaffAgainstStore() {
        Staff inStore = staff(12L, StaffRole.SENIOR, 3L, true);
        Staff otherStore = staff(13L, StaffRole.SENIOR, 99L, true);
        given(staffRepository.findById(12L)).willReturn(Optional.of(inStore));
        given(staffRepository.findById(13L)).willReturn(Optional.of(otherStore));

        service.update(1024L, request("김하늘", "010-2943-4821", 12L), STORE);
        service.update(1024L, request("김하늘", "010-2943-4821", 12L), HQ);
        assertThatThrownBy(() -> service.update(1024L, request("김하늘", "010-2943-4821", 13L), HQ))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("staffId"));
    }

    @Test
    @DisplayName("없는 담당 직원은 404 STAFF_NOT_FOUND")
    void should_throw404_when_staffMissing() {
        given(staffRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1024L, request("김하늘", "010-2943-4821", 404L), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STAFF_NOT_FOUND));
    }

    @Test
    @DisplayName("수정 입력 검사는 등록과 같다: 사이즈·이름·생일")
    void should_applySameInputRules() {
        CustomerUpdateRequest body = new CustomerUpdateRequest(" ", "Kim", "010-2943-4821", Nationality.DOMESTIC, 1899,
                "0230", null, com.crmpos.common.type.Gender.F, null,
                new CustomerRegisterRequest.Sizes(null, "110", null, null), null, null,
                new CustomerUpdateRequest.Marketing(false, false));

        assertThatThrownBy(() -> service.update(1024L, body, STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("name", "nameEn", "birthYear", "birthMd", "sizes.outer"));
    }

    @Test
    @DisplayName("마케팅 수신이 없고 전화번호도 틀리면 두 오류를 한 번에 돌려준다")
    void should_reportAllErrorsAtOnce_when_marketingMissingAndPhoneWrong() {
        CustomerUpdateRequest body = new CustomerUpdateRequest("김하늘", null, "abc", Nationality.DOMESTIC, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(1024L, body, STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("marketing", "phone"));
        verify(customerWriter, never()).update(any(), any(), any());
    }
}
