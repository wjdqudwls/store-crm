package com.crmpos.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import com.crmpos.common.type.Gender;
import com.crmpos.customer.controller.CustomerCreatedResponse;
import com.crmpos.customer.controller.CustomerRegisterRequest;
import com.crmpos.customer.controller.CustomerRegisterRequest.Address;
import com.crmpos.customer.controller.CustomerRegisterRequest.Agreements;
import com.crmpos.customer.controller.CustomerRegisterRequest.Sizes;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.customer.repository.CustomerRepository;
import java.time.LocalDateTime;
import java.util.List;
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
class CustomerRegistrationServiceTest {

    private static final String CRYPTO_KEY = "dGVzdC1vbmx5LWNyeXB0by1rZXktMzItYnl0ZXMhISE=";
    private static final String HMAC_KEY = "dGVzdC1vbmx5LWhtYWMta2V5LTMyLWJ5dGVzLW1vcmU=";
    private static final CurrentAccount STORE = new CurrentAccount(2L, StaffRole.STORE, 3L);

    @Mock
    CustomerRepository customerRepository;
    @Mock
    CustomerWriter customerWriter;
    @Mock
    StaffRepository staffRepository;

    final AesGcmCipher cipher = new AesGcmCipher(CRYPTO_KEY);
    final HmacHasher hasher = new HmacHasher(HMAC_KEY);
    CustomerRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerRegistrationService(customerRepository, customerWriter, staffRepository, cipher, hasher);
    }

    private static Agreements agreed() {
        return new Agreements(true, true, false, false);
    }

    private static RequestBuilder request() {
        return new RequestBuilder();
    }

    private static Customer savedAs(CustomerDraft draft, long id) {
        Customer customer = Customer.register(draft, LocalDateTime.of(2026, 11, 19, 10, 0));
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private void insertReturnsSavedCustomer() {
        given(customerWriter.insert(any(CustomerDraft.class), any(LocalDateTime.class)))
                .willAnswer(i -> savedAs(i.getArgument(0), 1024L));
    }

    @Test
    @DisplayName("정상 등록: 번호는 암호화·해시되어 저장되고 응답에는 뒷자리만 보인다")
    void should_encryptAndHashPhone_when_registered() {
        insertReturnsSavedCustomer();

        CustomerCreatedResponse response = service.register(request().build(), STORE);

        assertThat(response.id()).isEqualTo(1024L);
        assertThat(response.name()).isEqualTo("김하늘");
        assertThat(response.phoneMasked()).isEqualTo("****-4821");
        ArgumentCaptor<CustomerDraft> captor = ArgumentCaptor.forClass(CustomerDraft.class);
        verify(customerWriter).insert(captor.capture(), any());
        CustomerDraft draft = captor.getValue();
        assertThat(cipher.decrypt(draft.phoneEnc())).isEqualTo("+821029434821");
        assertThat(draft.phoneHash()).isEqualTo(hasher.hash("+821029434821"));
        assertThat(draft.phoneLast4()).isEqualTo("4821");
        assertThat(draft.homeStoreId()).isEqualTo(3L);
        assertThat(draft.source()).isEqualTo(CustomerSource.STORE);
    }

    @Test
    @DisplayName("같은 번호를 다른 모양으로 적어도 같은 해시라 중복으로 잡힌다")
    void should_detectDuplicate_when_samePhoneDifferentFormat() {
        Customer existing = savedAs(CustomerDraft.builder().name("김하늘").nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[1]).phoneHash("h").phoneLast4("4821").privacyAgreed(true).age14Confirmed(true)
                .source(CustomerSource.QR).build(), 1024L);
        given(customerRepository.findByPhoneHash(hasher.hash("+821029434821"))).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(request().phone("01029434821").build(), STORE))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_PHONE_DUPLICATED);
                    assertThat(e.getDetails()).isEqualTo(Map.of("customerId", 1024L, "name", "김하늘",
                            "phoneMasked", "****-4821"));
                });
        verify(customerWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("동시에 같은 번호가 저장돼 UNIQUE에 걸리면 먼저 저장된 고객으로 409를 준다")
    void should_return409_when_uniqueViolationOnInsert() {
        Customer winner = savedAs(CustomerDraft.builder().name("먼저").nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[1]).phoneHash("h").phoneLast4("4821").privacyAgreed(true).age14Confirmed(true)
                .source(CustomerSource.QR).build(), 7L);
        given(customerRepository.findByPhoneHash(any()))
                .willReturn(Optional.empty()).willReturn(Optional.of(winner));
        given(customerWriter.insert(any(), any())).willThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.register(request().build(), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_PHONE_DUPLICATED));
    }

    @Test
    @DisplayName("전화번호 형식이 틀리면 400 fieldErrors[phone], 입력값은 오류 내용에 없다")
    void should_returnPhoneFieldError_when_phoneInvalid() {
        assertThatThrownBy(() -> service.register(request().phone("010-ABCD-1234").build(), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> {
                    assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("phone");
                    assertThat(e.getFieldErrors().toString()).doesNotContain("ABCD");
                });
        verify(customerWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("외국인은 +로 시작하는 번호와 영문 이름이 필요하다")
    void should_requireNameEnAndPlus_when_foreign() {
        assertThatThrownBy(() -> service.register(request().nationality(Nationality.FOREIGN).name(null)
                .phone("010-2943-4821").build(), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("nameEn", "phone"));
    }

    @Test
    @DisplayName("외국인이 한글 이름을 비우면 영문 이름이 이름으로 들어간다")
    void should_useNameEnAsName_when_foreignWithoutName() {
        insertReturnsSavedCustomer();

        CustomerCreatedResponse response = service.register(request().nationality(Nationality.FOREIGN).name(null)
                .nameEn("Emma Smith").phone("+1 415 555 2671").build(), STORE);

        assertThat(response.name()).isEqualTo("Emma Smith");
        assertThat(response.phoneMasked()).isEqualTo("****-2671");
    }

    @Test
    @DisplayName("내국인이 영문 이름을 보내거나 이름이 없으면 400")
    void should_returnNameErrors_when_domesticNamesWrong() {
        assertThatThrownBy(() -> service.register(request().name(" ").nameEn("Kim").build(), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("name", "nameEn"));
    }

    @Test
    @DisplayName("여성 아우터 목록에 없는 110이나 아동 사이즈가 아닌 신발 값은 400 fieldErrors[sizes.*]")
    void should_returnSizeErrors_when_sizeNotAllowed() {
        CustomerRegisterRequest body = request().gender(Gender.F)
                .sizes(new Sizes("M", "110", "26", "212")).build();

        assertThatThrownBy(() -> service.register(body, STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("sizes.outer", "sizes.shoes"));
    }

    @Test
    @DisplayName("성별을 고르지 않으면 아우터 사이즈는 모든 허용값에서 고른다")
    void should_allowAnyOuterSize_when_genderMissing() {
        insertReturnsSavedCustomer();

        service.register(request().gender(null).sizes(new Sizes(null, "110", null, null)).build(), STORE);

        verify(customerWriter).insert(any(), any());
    }

    @Test
    @DisplayName("생일은 월일 4자리, 실제 달력이어야 한다 (0229는 허용, 0230·1301은 거절)")
    void should_checkBirthMonthDay() {
        assertThat(CustomerRegistrationService.validMonthDay("0229")).isTrue();
        assertThat(CustomerRegistrationService.validMonthDay("1231")).isTrue();
        assertThat(CustomerRegistrationService.validMonthDay("0230")).isFalse();
        assertThat(CustomerRegistrationService.validMonthDay("1301")).isFalse();
        assertThat(CustomerRegistrationService.validMonthDay("0000")).isFalse();
        assertThat(CustomerRegistrationService.validMonthDay("314")).isFalse();
    }

    @Test
    @DisplayName("태어난 해가 1900 이전이거나 미래면 400")
    void should_returnBirthYearError_when_outOfRange() {
        assertThatThrownBy(() -> service.register(request().birthYear(1899).build(), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("birthYear"));
        assertThatThrownBy(() -> service.register(request().birthYear(3000).build(), STORE))
                .isInstanceOf(ValidationFailedException.class);
    }

    private static Staff staff(long id, StaffRole role, Long storeId, boolean active) {
        Staff staff = mock(Staff.class);
        org.mockito.Mockito.lenient().when(staff.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(staff.getRole()).thenReturn(role);
        org.mockito.Mockito.lenient().when(staff.isActive()).thenReturn(active);
        Store store = mock(Store.class);
        org.mockito.Mockito.lenient().when(store.getId()).thenReturn(storeId);
        org.mockito.Mockito.lenient().when(staff.getStore()).thenReturn(store);
        return staff;
    }

    @Test
    @DisplayName("담당 직원은 그 매장의 활성 직원이어야 한다: 없으면 404, 다른 매장·비활성·매장 계정이면 400")
    void should_checkStaff() {
        given(staffRepository.findById(404L)).willReturn(Optional.empty());
        Staff other = staff(5L, StaffRole.STAFF, 99L, true);
        given(staffRepository.findById(5L)).willReturn(Optional.of(other));
        Staff inactive = staff(6L, StaffRole.STAFF, 3L, false);
        given(staffRepository.findById(6L)).willReturn(Optional.of(inactive));
        Staff storeAccount = staff(7L, StaffRole.STORE, 3L, true);
        given(staffRepository.findById(7L)).willReturn(Optional.of(storeAccount));

        assertThatThrownBy(() -> service.register(request().staffId(404L).build(), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STAFF_NOT_FOUND));
        for (long id : new long[] {5L, 6L, 7L}) {
            assertThatThrownBy(() -> service.register(request().staffId(id).build(), STORE))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                            assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("staffId"));
        }
    }

    @Test
    @DisplayName("그 매장의 활성 직원이면 담당 직원으로 저장된다")
    void should_saveStaffId_when_staffValid() {
        Staff staff = staff(12L, StaffRole.SENIOR, 3L, true);
        given(staffRepository.findById(12L)).willReturn(Optional.of(staff));
        insertReturnsSavedCustomer();

        service.register(request().staffId(12L).build(), STORE);

        ArgumentCaptor<CustomerDraft> captor = ArgumentCaptor.forClass(CustomerDraft.class);
        verify(customerWriter).insert(captor.capture(), any());
        assertThat(captor.getValue().staffId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("상세주소는 암호화되어 저장된다")
    void should_encryptAddressDetail() {
        insertReturnsSavedCustomer();

        service.register(request().address(new Address("서울", "강남구", "테헤란로 123 401호")).build(), STORE);

        ArgumentCaptor<CustomerDraft> captor = ArgumentCaptor.forClass(CustomerDraft.class);
        verify(customerWriter).insert(captor.capture(), any());
        assertThat(cipher.decrypt(captor.getValue().addressDetailEnc())).isEqualTo("테헤란로 123 401호");
        assertThat(captor.getValue().regionGu()).isEqualTo("강남구");
    }

    @Test
    @DisplayName("마케팅 동의는 동의한 것만 저장 값에 true로 넘어간다")
    void should_passMarketingFlags() {
        insertReturnsSavedCustomer();

        service.register(request().agreements(new Agreements(true, true, true, false)).build(), STORE);

        ArgumentCaptor<CustomerDraft> captor = ArgumentCaptor.forClass(CustomerDraft.class);
        verify(customerWriter).insert(captor.capture(), any());
        assertThat(captor.getValue().marketingSms()).isTrue();
        assertThat(captor.getValue().marketingKakao()).isFalse();
    }
}
