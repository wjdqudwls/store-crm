package com.crmpos.customer.service;

import com.crmpos.common.crypto.AesGcmCipher;
import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.controller.CustomerDetailResponse;
import com.crmpos.customer.controller.CustomerDetailResponse.ConfirmedBasis;
import com.crmpos.customer.controller.CustomerDetailResponse.Grade;
import com.crmpos.customer.controller.CustomerDetailResponse.Marketing;
import com.crmpos.customer.controller.CustomerDetailResponse.Profile;
import com.crmpos.customer.controller.CustomerDetailResponse.Ref;
import com.crmpos.customer.controller.CustomerDetailResponse.Sizes;
import com.crmpos.customer.controller.CustomerDetailResponse.Stats;
import com.crmpos.customer.controller.CustomerListItem;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.GradeChangeReason;
import com.crmpos.customer.domain.GradeDiscount;
import com.crmpos.customer.domain.Season;
import com.crmpos.customer.repository.CustomerListRow;
import com.crmpos.customer.repository.CustomerQueryMapper;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.customer.repository.GradeDiscountRepository;
import com.crmpos.customer.repository.GradeHistoryRepository;
import com.crmpos.customer.domain.PhoneNumber;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객 검색·상세 조회 (CUS-01·02). 다른 모듈(common)의 매장·직원은 이름만 따로 읽는다 (ADR-018). */
@Service
@RequiredArgsConstructor
public class CustomerQueryService {

    private static final Set<GradeChangeReason> PERIODIC_REASONS =
            Set.of(GradeChangeReason.PERIODIC, GradeChangeReason.PROVISIONAL_RECALC);

    private final CustomerQueryMapper queryMapper;
    private final CustomerRepository customerRepository;
    private final CustomerSummaryRepository summaryRepository;
    private final GradeDiscountRepository discountRepository;
    private final GradeHistoryRepository historyRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final AesGcmCipher cipher;

    /** 이름이 비어 있으면 전체를 내보내지 않고 빈 목록이다. */
    @Transactional(readOnly = true)
    public PageResponse<CustomerListItem> search(String name, int page, int size) {
        String keyword = name == null ? "" : name.trim();
        if (keyword.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        String escaped = escapeLike(keyword);
        List<CustomerListRow> rows = queryMapper.searchByName(escaped, size, page * size);
        long total = queryMapper.countByName(escaped);
        Map<Long, String> staffNames = staffNames(rows.stream().map(CustomerListRow::staffId).toList());
        List<CustomerListItem> items = rows.stream()
                .map(row -> CustomerListItem.from(row, row.staffId() == null ? null : staffNames.get(row.staffId())))
                .toList();
        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse get(Long id, boolean includeAddress) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        CustomerSummary summary = summaryRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("고객 요약이 없습니다. customerId=" + id));
        LocalDate today = LocalDate.now(KstTime.ZONE);

        Store store = customer.getHomeStoreId() == null ? null
                : storeRepository.findById(customer.getHomeStoreId()).orElse(null);
        Staff staff = customer.getStaffId() == null ? null
                : staffRepository.findById(customer.getStaffId()).orElse(null);
        int discountRate = discountRepository.findById(summary.getCurrentGrade())
                .map(GradeDiscount::getRate).orElse(0);
        ConfirmedBasis basis = historyRepository
                .findFirstByCustomerIdAndReasonInOrderByCreatedAtDescIdDesc(id, PERIODIC_REASONS)
                .map(h -> new ConfirmedBasis(h.getSeason(), h.getTicketCount(), h.getNetAmount(), h.getTotalScore()))
                .orElse(null);

        Grade grade = new Grade(summary.getCurrentGrade(), summary.getComputedGrade(), summary.getConfirmedGrade(),
                Season.isProvisional(today) ? "PROVISIONAL" : "CONFIRMED", Season.label(today),
                summary.getSeasonTicketCount(), summary.getTicketScore(), summary.getSeasonNetAmount(),
                summary.getAmountScore(), summary.getTotalScore(), discountRate, summary.getPolicyVersion(), basis);
        Stats stats = new Stats(KstTime.toOffset(summary.getFirstVisitAt()), KstTime.toOffset(summary.getLastVisitAt()),
                summary.getSeasonQty(), summary.getLifetimeAmount());
        Profile profile = new Profile(customer.getGender(), customer.getBirthYear(), customer.getBirthMd(),
                customer.getRegionSido(), customer.getRegionGu(),
                customer.getPreferredItemTypes() == null ? List.of() : customer.getPreferredItemTypes(),
                new Sizes(customer.getSizeTop(), customer.getSizeOuter(), customer.getSizeBottom(),
                        customer.getSizeShoes()),
                customer.getContactChannel());
        Marketing marketing = new Marketing(customer.getMarketingSmsAgreedAt() != null,
                customer.getMarketingKakaoAgreedAt() != null);
        String address = includeAddress && customer.getAddressDetailEnc() != null
                ? cipher.decrypt(customer.getAddressDetailEnc()) : null;

        return new CustomerDetailResponse(customer.getId(), customer.getName(), customer.getNameEn(),
                customer.getNationality(), PhoneNumber.display(cipher.decrypt(customer.getPhoneEnc())),
                KstTime.toOffset(customer.getCreatedAt()),
                store == null ? null : new Ref(store.getId(), store.getName()),
                staff == null ? null : new Ref(staff.getId(), staff.getName()),
                grade, stats, profile, marketing, address);
    }

    private Map<Long, String> staffNames(List<Long> staffIds) {
        List<Long> ids = staffIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return staffRepository.findAllById(ids).stream().collect(Collectors.toMap(Staff::getId, Staff::getName));
    }

    /** LIKE의 특수문자(! % _)를 글자 그대로 찾도록 '!'로 막는다. */
    static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
