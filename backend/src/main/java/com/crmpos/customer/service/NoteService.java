package com.crmpos.customer.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.BodyValidation;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.controller.NoteCreateRequest;
import com.crmpos.customer.controller.NoteItem;
import com.crmpos.customer.controller.NoteUpdateRequest;
import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.NoteType;
import com.crmpos.customer.repository.ConsultNoteRepository;
import com.crmpos.customer.repository.CustomerRepository;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상담 메모 (MEM-01~03, CAL-02, 03-api.md 3.3). 쓰기는 매장 계정(작성 직원을 고름)과 직원 개인 계정(본인)만 하고,
 * 고치고 지우는 것은 작성 직원 본인(개인 계정)·그 매장의 매장 계정·본사다.
 * 메모 내용은 로그에도 예외 메시지에도 남기지 않는다 (NFR-04b).
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private static final Set<StaffRole> EMPLOYEE_RANKS =
            EnumSet.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private final ConsultNoteRepository noteRepository;
    private final CustomerRepository customerRepository;
    private final StaffRepository staffRepository;
    private final Validator validator;

    @Transactional
    public NoteItem create(Long customerId, NoteCreateRequest request, CurrentAccount account) {
        requireWriter(account);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        List<FieldError> errors = new ArrayList<>(BodyValidation.violations(validator, request));
        if (request.type() == NoteType.RESERVATION) {
            // 방문 예약은 날짜로 할 일을 만들어야 해서 후속 연락 작업(B4-3)에서 연다
            BodyValidation.addIfAbsent(errors, new FieldError("type", "방문 예약 메모는 아직 쓸 수 없습니다."));
        }
        if (request.reminder() != null) {
            // 조용히 버리면 직원이 알림이 걸린 줄 안다. 할 일 만들기는 B4-3에서 한다
            BodyValidation.addIfAbsent(errors, new FieldError("reminder", "재연락 날짜는 아직 쓸 수 없습니다."));
        }
        if (request.type() == NoteType.CALL) {
            if (request.callResult() == null) {
                BodyValidation.addIfAbsent(errors, new FieldError("callResult", "연락 결과를 골라 주세요."));
            }
            if (request.channel() == null) {
                BodyValidation.addIfAbsent(errors, new FieldError("channel", "연락 방법을 골라 주세요."));
            }
        }
        Staff author = findAuthor(request.staffId(), account, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        ConsultNote note = noteRepository.save(ConsultNote.write(customer, author.getId(), account.id(),
                request.type(), request.callResult(), request.channel(), request.content(), now));
        return NoteItem.from(note, author.getName(), canEdit(note, account, author));
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteItem> list(Long customerId, int page, int size, CurrentAccount account) {
        if (!customerRepository.existsById(customerId)) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        Page<ConsultNote> result = noteRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId,
                PageRequest.of(page, size));
        Map<Long, Staff> authors = staffRepository
                .findAllById(result.getContent().stream().map(ConsultNote::getStaffId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Staff::getId, s -> s));
        List<NoteItem> items = result.getContent().stream().map(n -> {
            Staff author = authors.get(n.getStaffId());
            return NoteItem.from(n, author == null ? null : author.getName(), canEdit(n, account, author));
        }).toList();
        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    /** 검사 순서: 메모 없음 404 → 권한 403 → 입력 400. */
    @Transactional
    public NoteItem update(Long noteId, NoteUpdateRequest request, CurrentAccount account) {
        ConsultNote note = findEditable(noteId, account);
        List<FieldError> errors = BodyValidation.violations(validator, request);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        note.edit(request.content(), LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS));
        Staff author = staffRepository.findById(note.getStaffId()).orElse(null);
        return NoteItem.from(note, author == null ? null : author.getName(), true);
    }

    @Transactional
    public void delete(Long noteId, CurrentAccount account) {
        noteRepository.delete(findEditable(noteId, account));
    }

    private ConsultNote findEditable(Long noteId, CurrentAccount account) {
        ConsultNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_NOT_FOUND));
        Staff author = staffRepository.findById(note.getStaffId()).orElse(null);
        if (!canEdit(note, account, author)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return note;
    }

    /** 본사는 전체, 매장 계정은 그 매장(작성 직원의 매장)의 메모, 직원 개인 계정은 자기가 쓴 메모만 (MEM-03). */
    private static boolean canEdit(ConsultNote note, CurrentAccount account, Staff author) {
        return switch (account.role()) {
            case HQ -> true;
            case STORE -> author != null && author.getStore() != null && author.getStore().getId().equals(account.storeId());
            case MANAGER, SENIOR, JUNIOR, STAFF -> note.getStaffId().equals(account.id());
            default -> false;
        };
    }

    /** 메모는 매장 계정과 직원 개인 계정이 쓴다. 본사·키오스크는 쓰지 못한다 (03 3.3). */
    private static void requireWriter(CurrentAccount account) {
        account.requireRole(StaffRole.STORE, StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);
    }

    /** 매장 계정이면 고른 직원(그 매장의 활성 직원), 직원 개인 계정이면 본인이 작성 직원이다. */
    private Staff findAuthor(Long staffId, CurrentAccount account, List<FieldError> errors) {
        if (account.role() != StaffRole.STORE) {
            return staffRepository.findById(account.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_INVALID));
        }
        if (staffId == null) {
            errors.add(new FieldError("staffId", "작성 직원을 골라 주세요."));
            return null;
        }
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        boolean sameStore = staff.getStore() != null && staff.getStore().getId().equals(account.storeId());
        if (!staff.isActive() || !sameStore || !EMPLOYEE_RANKS.contains(staff.getRole())) {
            errors.add(new FieldError("staffId", "이 매장의 활성 직원이 아닙니다."));
            return null;
        }
        return staff;
    }
}
