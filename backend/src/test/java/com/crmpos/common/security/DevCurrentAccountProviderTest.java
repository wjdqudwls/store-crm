package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class DevCurrentAccountProviderTest {

    @Mock
    StaffRepository staffRepository;

    @InjectMocks
    DevCurrentAccountProvider provider;

    private static Staff staff(Long id, StaffRole role, Long storeId, boolean active) {
        Staff staff = mock(Staff.class);
        lenient().when(staff.getId()).thenReturn(id);
        lenient().when(staff.getRole()).thenReturn(role);
        lenient().when(staff.isActive()).thenReturn(active);
        if (storeId != null) {
            Store store = mock(Store.class);
            lenient().when(store.getId()).thenReturn(storeId);
            lenient().when(staff.getStore()).thenReturn(store);
        }
        return staff;
    }

    private static MockHttpServletRequest requestWithHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevCurrentAccountProvider.HEADER, value);
        return request;
    }

    @Test
    @DisplayName("헤더의 계정 ID로 매장 계정의 ID·역할·매장 ID를 꺼낸다")
    void should_returnStoreAccount_when_headerHasId() {
        Staff found = staff(7L, StaffRole.STORE, 3L, true);
        given(staffRepository.findById(7L)).willReturn(Optional.of(found));

        CurrentAccount account = provider.current(requestWithHeader("7"));

        assertThat(account).isEqualTo(new CurrentAccount(7L, StaffRole.STORE, 3L));
    }

    @Test
    @DisplayName("본사 계정은 매장 ID가 없다")
    void should_haveNoStoreId_when_hqAccount() {
        Staff found = staff(1L, StaffRole.HQ, null, true);
        given(staffRepository.findById(1L)).willReturn(Optional.of(found));

        CurrentAccount account = provider.current(requestWithHeader("1"));

        assertThat(account.role()).isEqualTo(StaffRole.HQ);
        assertThat(account.storeId()).isNull();
    }

    @Test
    @DisplayName("헤더가 없으면 첫 활성 매장 계정으로 본다")
    void should_useDefaultStoreAccount_when_noHeader() {
        Staff found = staff(2L, StaffRole.STORE, 1L, true);
        given(staffRepository.findFirstByRoleAndActiveTrueOrderByIdAsc(StaffRole.STORE)).willReturn(Optional.of(found));

        CurrentAccount account = provider.current(new MockHttpServletRequest());

        assertThat(account).isEqualTo(new CurrentAccount(2L, StaffRole.STORE, 1L));
    }

    @Test
    @DisplayName("헤더도 없고 매장 계정도 없으면 401")
    void should_throw401_when_noHeaderAndNoStoreAccount() {
        given(staffRepository.findFirstByRoleAndActiveTrueOrderByIdAsc(StaffRole.STORE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> provider.current(new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("없는 계정 ID면 401")
    void should_throw401_when_unknownAccountId() {
        given(staffRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> provider.current(requestWithHeader("999")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("비활성 계정이면 401")
    void should_throw401_when_inactiveAccount() {
        Staff found = staff(5L, StaffRole.STAFF, 3L, false);
        given(staffRepository.findById(5L)).willReturn(Optional.of(found));

        assertThatThrownBy(() -> provider.current(requestWithHeader("5")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    @DisplayName("숫자가 아닌 헤더 값이면 401")
    void should_throw401_when_headerNotNumber() {
        assertThatThrownBy(() -> provider.current(requestWithHeader("abc")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }
}
