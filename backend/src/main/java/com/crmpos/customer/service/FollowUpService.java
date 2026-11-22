package com.crmpos.customer.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.controller.FollowUpCompleteRequest;
import com.crmpos.customer.controller.FollowUpItem;
import com.crmpos.customer.controller.FollowUpTodayItem;
import com.crmpos.customer.controller.NoteCreateRequest;
import com.crmpos.customer.controller.NoteItem;
import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.FollowUpKind;
import com.crmpos.customer.domain.FollowUpTask;
import com.crmpos.customer.domain.FollowUpTaskStatus;
import com.crmpos.customer.domain.NoteType;
import com.crmpos.customer.domain.PhoneNumber;
import com.crmpos.customer.repository.ConsultNoteRepository;
import com.crmpos.customer.repository.CustomerPurchaseRepository;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.customer.repository.FollowUpTaskRepository;
import com.crmpos.customer.repository.SaleSoldAt;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후속 연락 할 일: 오늘 할 일, 고객 상세의 할 일, 연락 결과로 완료·건너뛰기·방문함 (CAL-06·10, 03-api.md 3.3·4.7).
 * 직원 개인 계정은 내 할 일만 처리하고, 매장 계정은 그 매장 직원·매장 계정이 받는 할 일을 처리한다.
 * 메모 내용·고객 이름은 로그에 남기지 않는다 (NFR-04b).
 */
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private static final Set<StaffRole> EMPLOYEE_RANKS =
            Set.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private final FollowUpTaskRepository taskRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSummaryRepository summaryRepository;
    private final CustomerPurchaseRepository purchaseRepository;
    private final ConsultNoteRepository noteRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final NoteService noteService;

    // --- 조회 ---

    /**
     * 오늘 할 일: 만기일이 오늘이거나 지난 열린 할 일, 만기일 오래된 순. 직원은 내 할 일, 매장 계정은 그 매장 전체
     * (매장 계정이 맡은 고객 포함), 본사는 storeId를 지정한다 (CAL-06).
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowUpTodayItem> today(CurrentAccount account, Long storeId, int page, int size) {
        Set<Long> staffIds = switch (account.role()) {
            case STORE -> staffIdsOfStore(account.storeId());
            case HQ -> {
                if (storeId == null) {
                    throw new ValidationFailedException(List.of(new FieldError("storeId", "매장을 골라 주세요.")));
                }
                if (!storeRepository.existsById(storeId)) {
                    throw new BusinessException(ErrorCode.STORE_NOT_FOUND);
                }
                yield staffIdsOfStore(storeId);
            }
            default -> Set.of(account.id());
        };
        LocalDate today = LocalDate.now(KstTime.ZONE);
        Page<FollowUpTask> result = taskRepository.findDue(FollowUpTaskStatus.OPEN, today, staffIds,
                PageRequest.of(page, size, Sort.by("dueDate", "id")));
        List<FollowUpTask> tasks = result.getContent();

        Lookups lookups = lookups(tasks);
        Map<Long, CustomerSummary> summaries = summaryRepository
                .findAllById(tasks.stream().map(t -> t.getCustomer().getId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CustomerSummary::getCustomerId, s -> s));
        List<FollowUpTodayItem> items = tasks.stream().map(t -> {
            Customer c = t.getCustomer();
            Staff staff = lookups.staff().get(t.getStaffId());
            return new FollowUpTodayItem(t.getId(), c.getId(), c.getName(), PhoneNumber.masked(c.getPhoneLast4()),
                    summaries.get(c.getId()) == null ? null : summaries.get(c.getId()).getCurrentGrade(),
                    t.getRuleName(), t.isPromotional(), t.getDueDate(),
                    Math.max(0, ChronoUnit.DAYS.between(t.getDueDate(), today)), t.getKind(),
                    lookups.noteContent(t), t.getSaleId(), lookups.soldAt(t),
                    c.getMarketingSmsAgreedAt() != null || c.getMarketingKakaoAgreedAt() != null,
                    assignee(staff), t.getStaffId(), staff == null ? null : staff.getName());
        }).toList();
        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    /** 고객 상세의 할 일: 열린 것 먼저, 그 안에서 만기일 순. */
    @Transactional(readOnly = true)
    public PageResponse<FollowUpItem> ofCustomer(Long customerId, int page, int size) {
        if (!customerRepository.existsById(customerId)) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        Page<FollowUpTask> result = taskRepository.findOfCustomer(customerId, PageRequest.of(page, size));
        Lookups lookups = lookups(result.getContent());
        List<FollowUpItem> items = result.getContent().stream().map(t -> item(t, lookups)).toList();
        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    // --- 처리 ---

    /**
     * 연락 결과를 기록하고 완료한다 (CAL-02·06). 연락 메모(CALL)를 만들어 할 일에 연결한다.
     * 검사 순서: 할 일 없음 404 → 권한 403 → 이미 닫힘 409 → 입력 400.
     */
    @Transactional
    public FollowUpItem complete(Long taskId, FollowUpCompleteRequest request, CurrentAccount account) {
        FollowUpTask task = findHandleable(taskId, account);
        NoteItem note = noteService.create(task.getCustomer().getId(), new NoteCreateRequest(NoteType.CALL,
                request.content(), request.staffId(), request.callResult(), request.channel(), request.reminder()), account);
        task.complete(note.id(), now());
        return item(task, lookups(List.of(task)));
    }

    @Transactional
    public FollowUpItem skip(Long taskId, CurrentAccount account) {
        FollowUpTask task = findHandleable(taskId, account);
        task.skip(now());
        return item(task, lookups(List.of(task)));
    }

    /** 방문 예약(VISIT)을 '방문함'으로 완료한다 (CAL-10). 방문 예약이 아니면 400. */
    @Transactional
    public FollowUpItem visited(Long taskId, CurrentAccount account) {
        FollowUpTask task = findHandleable(taskId, account);
        if (task.getKind() != FollowUpKind.VISIT) {
            throw new ValidationFailedException(List.of(new FieldError("kind", "방문 예약 할 일만 방문함으로 처리할 수 있습니다.")));
        }
        task.markVisited(now());
        return item(task, lookups(List.of(task)));
    }

    /** 할 일을 찾고 처리할 권한과 열림 상태를 확인한다. 404 → 403 → 409. */
    private FollowUpTask findHandleable(Long taskId, CurrentAccount account) {
        FollowUpTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLLOW_UP_NOT_FOUND));
        if (!canHandle(task, account)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!task.isOpen()) {
            throw new BusinessException(ErrorCode.FOLLOW_UP_ALREADY_CLOSED);
        }
        return task;
    }

    /** 직원은 내 할 일만, 매장 계정은 그 매장 소속 계정(직원·매장 계정)이 받는 할 일. 본사·키오스크는 못 한다. */
    private boolean canHandle(FollowUpTask task, CurrentAccount account) {
        if (EMPLOYEE_RANKS.contains(account.role())) {
            return task.getStaffId().equals(account.id());
        }
        if (account.role() == StaffRole.STORE) {
            return staffRepository.findById(task.getStaffId())
                    .map(s -> s.getStore() != null && s.getStore().getId().equals(account.storeId())).orElse(false);
        }
        return false;
    }

    // --- 조립 ---

    private Set<Long> staffIdsOfStore(Long storeId) {
        return staffRepository.findByStoreIdOrderByIdAsc(storeId).stream().map(Staff::getId)
                .collect(Collectors.toSet());
    }

    private static String assignee(Staff staff) {
        return staff != null && staff.getRole() == StaffRole.STORE ? "STORE" : "STAFF";
    }

    private FollowUpItem item(FollowUpTask t, Lookups lookups) {
        Staff staff = lookups.staff().get(t.getStaffId());
        return new FollowUpItem(t.getId(), t.getKind(), t.getRuleName(), t.isPromotional(), t.getSaleId(),
                lookups.soldAt(t), lookups.noteContent(t), t.getDueDate(), t.getStatus(), assignee(staff),
                t.getStaffId(), staff == null ? null : staff.getName(),
                t.getClosedAt() == null ? null : KstTime.toOffset(t.getClosedAt()));
    }

    /** 할 일 목록에 필요한 받는 사람·판매 시각·메모 내용을 한 번에 읽어 둔다 (N+1을 피한다). */
    private Lookups lookups(Collection<FollowUpTask> tasks) {
        Map<Long, Staff> staff = new HashMap<>();
        staffRepository.findAllById(tasks.stream().map(FollowUpTask::getStaffId).collect(Collectors.toSet()))
                .forEach(s -> staff.put(s.getId(), s));
        Set<Long> saleIds = tasks.stream().map(FollowUpTask::getSaleId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, LocalDateTime> soldAt = new HashMap<>();
        if (!saleIds.isEmpty()) {
            for (SaleSoldAt s : purchaseRepository.findSoldAtOfSales(saleIds)) {
                soldAt.put(s.saleId(), s.soldAt());
            }
        }
        Set<Long> noteIds = tasks.stream().map(FollowUpTask::getSourceNoteId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> noteContent = new HashMap<>();
        if (!noteIds.isEmpty()) {
            noteRepository.findAllById(noteIds).forEach((ConsultNote n) -> noteContent.put(n.getId(), n.getContent()));
        }
        return new Lookups(staff, soldAt, noteContent);
    }

    private record Lookups(Map<Long, Staff> staff, Map<Long, LocalDateTime> soldAtBySale,
            Map<Long, String> contentByNote) {

        OffsetDateTime soldAt(FollowUpTask t) {
            LocalDateTime sold = t.getSaleId() == null ? null : soldAtBySale.get(t.getSaleId());
            return sold == null ? null : KstTime.toOffset(sold);
        }

        String noteContent(FollowUpTask t) {
            return t.getSourceNoteId() == null ? null : contentByNote.get(t.getSourceNoteId());
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
    }
}
