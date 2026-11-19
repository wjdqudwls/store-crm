package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.web.MockHttpServletRequest;

/** 데모 계정과 임시 계정 구현을 실제 DB로 확인한다 (B0-6). */
@IntegrationTest
class DevAccountIntegrationTest {

    @Autowired
    DevCurrentAccountProvider provider;

    @Autowired
    DevAccountSeeder seeder;

    @Autowired
    StaffRepository staffRepository;

    @Autowired
    StoreRepository storeRepository;

    @Test
    @DisplayName("헤더가 없으면 데모 매장 계정으로 매장 ID를 꺼낸다")
    void should_returnStoreId_when_defaultStoreAccount() {
        CurrentAccount account = provider.current(new MockHttpServletRequest());

        assertThat(account.role()).isEqualTo(StaffRole.STORE);
        assertThat(account.storeId()).isEqualTo(storeRepository.findByCode("GN01").orElseThrow().getId());
    }

    @Test
    @DisplayName("헤더로 홍대점 매장 계정을 고르면 홍대점 ID가 나온다")
    void should_returnHongdaeStoreId_when_headerPicksIt() {
        Long hongdaeAccountId = staffRepository.findAll().stream()
                .filter(s -> s.getLoginId().equals("store-hd01")).findFirst().orElseThrow().getId();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevCurrentAccountProvider.HEADER, String.valueOf(hongdaeAccountId));

        CurrentAccount account = provider.current(request);

        assertThat(account.storeId()).isEqualTo(storeRepository.findByCode("HD01").orElseThrow().getId());
    }

    @Test
    @DisplayName("본사 계정은 매장 ID 없이 꺼내진다")
    void should_returnNullStoreId_when_hqAccount() {
        Long hqId = staffRepository.findAll().stream()
                .filter(s -> s.getRole() == StaffRole.HQ).findFirst().orElseThrow().getId();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevCurrentAccountProvider.HEADER, String.valueOf(hqId));

        CurrentAccount account = provider.current(request);

        assertThat(account.role()).isEqualTo(StaffRole.HQ);
        assertThat(account.storeId()).isNull();
    }

    @Test
    @DisplayName("데모 계정 넣기를 다시 실행해도 계정이 늘지 않는다")
    void should_notDuplicate_when_seederRunsAgain() {
        long before = staffRepository.count();

        seeder.run(new DefaultApplicationArguments());

        assertThat(staffRepository.count()).isEqualTo(before);
        assertThat(before).isEqualTo(8);
    }
}
