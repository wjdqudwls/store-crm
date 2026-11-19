package com.crmpos.sales.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.api.CustomerApi;
import com.crmpos.sales.controller.DailySummaryItem;
import com.crmpos.sales.controller.PurchaseItem;
import com.crmpos.sales.controller.SaleDetailResponse;
import com.crmpos.sales.controller.SaleListItem;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleLine;
import com.crmpos.sales.domain.StoreDayClose;
import com.crmpos.sales.domain.StoreDayCloseId;
import com.crmpos.sales.repository.SaleRepository;
import com.crmpos.sales.repository.StoreDayCloseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매 조회 (SAL-05, CUS-03, ADM-03): 판매 내역, 영수증 상세, 고객 구매이력, 하루 판매 요약.
 * 다른 모듈의 이름(고객·직원·매장)은 목록 전체를 한 번에 읽는다 (목록 N+1 없음, 03 4.12).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleQueryService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final SaleRepository saleRepository;
    private final StoreDayCloseRepository dayCloseRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerApi customerApi;
    private final SaleDetailBuilder detailBuilder;

    /** 매장별 그날 판매 수(기록 취소 제외)와 마감 여부. 매장 계정은 자기 매장 1개, 본사는 전 매장 또는 지정한 매장. */
    public List<DailySummaryItem> dailySummary(LocalDate date, Long storeId, CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        LocalDate day = date != null ? date : LocalDate.now(KstTime.ZONE);
        List<Long> storeIds;
        if (account.role() == StaffRole.STORE) {
            storeIds = List.of(account.storeId());
        } else if (storeId != null) {
            if (!storeRepository.existsById(storeId)) {
                throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
            }
            storeIds = List.of(storeId);
        } else {
            storeIds = storeRepository.findAll().stream().filter(Store::isActive).map(Store::getId).toList();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : saleRepository.countByStore(day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        List<DailySummaryItem> items = new ArrayList<>();
        for (Long id : storeIds) {
            StoreDayClose close = dayCloseRepository.findById(new StoreDayCloseId(id, day)).orElse(null);
            boolean closed = close != null && close.isClosed();
            items.add(new DailySummaryItem(id, day, counts.getOrDefault(id, 0L), closed,
                    closed ? KstTime.toOffset(close.getClosedAt()) : null));
        }
        return items;
    }

    /** 판매 내역: 매장 계정은 자기 매장, 본사는 storeId가 필요하다. 기록 취소된 판매도 status로 보인다. */
    public PageResponse<SaleListItem> list(LocalDate from, LocalDate to, Long storeId, int page, int size,
            CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        Long targetStore;
        if (account.role() == StaffRole.STORE) {
            targetStore = account.storeId();
        } else {
            if (storeId == null) {
                throw new ValidationFailedException(List.of(new FieldError("storeId", "본사는 매장을 골라야 합니다.")));
            }
            targetStore = storeId;
        }
        LocalDate end = to != null ? to : LocalDate.now(KstTime.ZONE);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if (start.isAfter(end) || start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new ValidationFailedException(List.of(new FieldError("from",
                    "기간은 시작일이 끝일보다 늦지 않고 366일 이내여야 합니다.")));
        }
        Page<Sale> result = saleRepository.findHistory(targetStore, start.atStartOfDay(),
                end.plusDays(1).atStartOfDay(), PageRequest.of(page, size));
        Map<Long, String> customerNames = customerApi.findNames(result.getContent().stream()
                .map(Sale::getCustomerId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> staffNames = staffNames(result.getContent().stream().map(Sale::getStaffId).toList());
        List<SaleListItem> items = result.getContent().stream()
                .map(s -> SaleListItem.from(s, customerNames.get(s.getCustomerId()), staffNames.get(s.getStaffId())))
                .toList();
        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    /** 영수증 상세. 다른 매장 영수증도 보기만 한다 (SAL-03, 2026-11-18). */
    public SaleDetailResponse detail(Long saleId, CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        Sale sale = saleRepository.findDetailById(saleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));
        return detailBuilder.build(sale);
    }

    /** 고객 구매이력 (sales 모듈이 제공): 기록 취소 제외, 최신순. keyword가 있으면 상품명·품번에 그 말이 든 줄만 담는다. */
    public PageResponse<PurchaseItem> purchases(Long customerId, String keyword, int page, int size,
            CurrentAccount account) {
        account.requireRole(StaffRole.HQ, StaffRole.STORE, StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR,
                StaffRole.STAFF);
        String like = likePattern(keyword);
        Page<Long> ids = saleRepository.findPurchaseIds(customerId, like, PageRequest.of(page, size));
        List<Sale> sales = ids.isEmpty() ? List.of() : saleRepository.findWithLinesByIdIn(ids.getContent());
        Map<Long, Sale> byId = sales.stream().collect(Collectors.toMap(Sale::getId, s -> s));
        Map<Long, String> storeNames = storeRepository.findAllById(sales.stream().map(Sale::getStoreId).distinct()
                .toList()).stream().collect(Collectors.toMap(Store::getId, Store::getName));
        List<PurchaseItem> items = ids.getContent().stream().map(byId::get).filter(Objects::nonNull)
                .map(s -> PurchaseItem.from(s, storeNames.get(s.getStoreId()), matchingLines(s, like))).toList();
        return PageResponse.of(items, page, size, ids.getTotalElements());
    }

    private static List<SaleLine> matchingLines(Sale sale, String like) {
        if (like == null) {
            return sale.getLines();
        }
        String needle = like.substring(1, like.length() - 1).replace("!%", "%").replace("!_", "_").replace("!!", "!");
        return sale.getLines().stream().filter(l -> l.getProductName().toLowerCase().contains(needle)
                || (l.getProductCode() != null && l.getProductCode().toLowerCase().contains(needle))).toList();
    }

    /** 검색어를 소문자로 바꾸고 LIKE 특수문자를 '!'로 막아 %…% 로 감싼다. 비었으면 null. */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private Map<Long, String> staffNames(List<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (Staff staff : staffRepository.findAllById(ids.stream().distinct().toList())) {
            names.put(staff.getId(), staff.getName());
        }
        return names;
    }
}
