package com.crmpos.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crmpos.common.controller.StaffItem;
import com.crmpos.common.controller.StoreItem;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreQueryServiceTest {

    @Mock
    StoreRepository storeRepository;
    @Mock
    StaffRepository staffRepository;
    @InjectMocks
    StoreQueryService service;

    private static final CurrentAccount HQ = new CurrentAccount(1L, StaffRole.HQ, null);
    private static final CurrentAccount STORE_1 = new CurrentAccount(3L, StaffRole.STORE, 1L);

    private static Store store(long id, String code, String name) {
        Store store = mock(Store.class);
        given(store.getId()).willReturn(id);
        given(store.getCode()).willReturn(code);
        given(store.getName()).willReturn(name);
        return store;
    }

    private static StaffRepository.StaffCount count(long storeId, long count) {
        StaffRepository.StaffCount c = mock(StaffRepository.StaffCount.class);
        given(c.getStoreId()).willReturn(storeId);
        given(c.getCount()).willReturn(count);
        return c;
    }

    @Test
    @DisplayName("본사는 매장 목록을 활성 직원 수와 함께 받고 직원이 없는 매장은 0이다")
    void should_listStoresWithCounts_when_hq() {
        Store gn = store(1L, "GN01", "강남점");
        Store sc = store(2L, "SC02", "서초점");
        given(storeRepository.findAllByOrderByIdAsc()).willReturn(List.of(gn, sc));
        StaffRepository.StaffCount gnCount = count(1L, 4);
        given(staffRepository.countActiveByStore(any())).willReturn(List.of(gnCount));

        List<StoreItem> items = service.listStores(HQ);

        assertThat(items).extracting(StoreItem::code, StoreItem::activeStaffCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GN01", 4),
                        org.assertj.core.groups.Tuple.tuple("SC02", 0));
    }

    @Test
    @DisplayName("본사가 아니면 매장 목록은 403")
    void should_forbidStoreList_when_notHq() {
        assertThatThrownBy(() -> service.listStores(STORE_1)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("매장 계정은 자기 매장의 활성 직원만 받는다")
    void should_listActiveStaffOnly_when_ownStore() {
        given(storeRepository.existsById(1L)).willReturn(true);
        Staff senior = mock(Staff.class);
        given(senior.getId()).willReturn(17L);
        given(senior.getName()).willReturn("이지은");
        given(senior.getRole()).willReturn(StaffRole.SENIOR);
        given(staffRepository.findByStoreIdAndActiveTrueAndRoleInOrderByIdAsc(any(), any()))
                .willReturn(List.of(senior));

        List<StaffItem> items = service.listStaff(1L, STORE_1);

        assertThat(items).extracting(StaffItem::id, StaffItem::role).containsExactly(
                org.assertj.core.groups.Tuple.tuple(17L, StaffRole.SENIOR));
        verify(staffRepository, never()).findByStoreIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("본사는 매장의 모든 계정을 받는다")
    void should_listAllAccounts_when_hq() {
        given(storeRepository.existsById(1L)).willReturn(true);
        given(staffRepository.findByStoreIdOrderByIdAsc(1L)).willReturn(List.of(mock(Staff.class)));

        assertThat(service.listStaff(1L, HQ)).hasSize(1);
    }

    @Test
    @DisplayName("다른 매장의 계정 목록은 매장이 있는지와 관계없이 403")
    void should_forbid_when_otherStore() {
        assertThatThrownBy(() -> service.listStaff(2L, STORE_1)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> service.listStaff(999L, STORE_1)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("직원·키오스크 계정은 403, 없는 매장은 본사에게 404")
    void should_forbidStaffRoles_and404_when_missingStore() {
        assertThatThrownBy(() -> service.listStaff(1L, new CurrentAccount(17L, StaffRole.SENIOR, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
        given(storeRepository.existsById(999L)).willReturn(false);
        assertThatThrownBy(() -> service.listStaff(999L, HQ)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.STORE_NOT_FOUND);
    }
}
