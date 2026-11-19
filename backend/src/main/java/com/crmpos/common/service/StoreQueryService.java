package com.crmpos.common.service;

import com.crmpos.common.controller.StaffItem;
import com.crmpos.common.controller.StoreItem;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장·계정 목록 조회 (ADM-01·03, 03-api.md 3.7). 판매 데이터는 읽지 않는다 (순환 방지, 03 3.7).
 * 매장 목록은 본사만, 계정 목록은 본사(모든 계정)와 자기 매장의 매장 계정(활성 직원만)이 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    /** 직원 직급(개인 계정). 판매 사원·처리 직원으로 고를 수 있는 역할이다. */
    private static final Set<StaffRole> STAFF_ROLES = EnumSet.of(StaffRole.MANAGER, StaffRole.SENIOR,
            StaffRole.JUNIOR, StaffRole.STAFF);

    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;

    public List<StoreItem> listStores(CurrentAccount account) {
        account.requireRole(StaffRole.HQ);
        Map<Long, Integer> counts = new HashMap<>();
        staffRepository.countActiveByStore(STAFF_ROLES)
                .forEach(c -> counts.put(c.getStoreId(), (int) c.getCount()));
        return storeRepository.findAllByOrderByIdAsc().stream()
                .map(store -> StoreItem.from(store, counts.getOrDefault(store.getId(), 0))).toList();
    }

    /** 권한 → 매장 존재 순서로 검사한다: 다른 매장은 있는지 없는지 알려 주지 않고 403. */
    public List<StaffItem> listStaff(Long storeId, CurrentAccount account) {
        account.requireRole(StaffRole.HQ, StaffRole.STORE);
        if (account.role() == StaffRole.STORE && !storeId.equals(account.storeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
        }
        if (account.role() == StaffRole.HQ) {
            return staffRepository.findByStoreIdOrderByIdAsc(storeId).stream().map(StaffItem::from).toList();
        }
        return staffRepository.findByStoreIdAndActiveTrueAndRoleInOrderByIdAsc(storeId, STAFF_ROLES).stream()
                .map(StaffItem::from).toList();
    }
}
