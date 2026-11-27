package com.crmpos.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.common.domain.ManagerSlot;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.web.KstTime;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장마다 메인 매니저 1명·임시 매니저 1명 (BR-15): UNIQUE(store_id, manager_slot)이 지킨다. 매니저를 만들거나 바꾸는
 * API(직원 관리 B4-6, 승격·자리 비우기)는 만들지 않기로 했으므로 제약만 확인한다. 롤백한다.
 */
@IntegrationTest
@Transactional
class ManagerSlotConstraintTest {

    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;

    private Staff manager(String loginId, String storeCode, ManagerSlot slot) {
        Store store = storeRepository.findByCode(storeCode).orElseThrow();
        return Staff.create(loginId, "x", "가상 매니저", StaffRole.MANAGER, slot, store, null,
                LocalDateTime.now(KstTime.ZONE));
    }

    @Test
    @DisplayName("같은 매장에 두 번째 메인 매니저는 넣을 수 없다")
    void should_reject_secondMainManager() {
        // 시드의 gn01-manager가 강남점 메인이다
        assertThatThrownBy(() -> staffRepository.saveAndFlush(manager("slot-main-2", "GN01", ManagerSlot.MAIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("임시 매니저 1명과 다른 매장의 메인 1명은 넣을 수 있고, 같은 매장의 두 번째 임시는 안 된다")
    void should_allowOneTemp_andMainInOtherStore() {
        assertThat(staffRepository.saveAndFlush(manager("slot-temp-1", "GN01", ManagerSlot.TEMP)).getId()).isNotNull();
        assertThat(staffRepository.saveAndFlush(manager("slot-main-hd", "HD01", ManagerSlot.MAIN)).getId()).isNotNull();

        assertThatThrownBy(() -> staffRepository.saveAndFlush(manager("slot-temp-2", "GN01", ManagerSlot.TEMP)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
