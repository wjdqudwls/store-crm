package com.crmpos.customer.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.domain.NotificationType;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.service.StaffNotifier;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.BodyValidation;
import com.crmpos.common.web.KstTime;
import com.crmpos.common.web.PageResponse;
import com.crmpos.customer.controller.NoteCreateRequest;
import com.crmpos.customer.controller.NoteItem;
import com.crmpos.customer.controller.NoteUpdateRequest;
import com.crmpos.customer.domain.ConsultNote;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.FollowUpKind;
import com.crmpos.customer.domain.FollowUpTask;
import com.crmpos.customer.domain.NoteType;
import com.crmpos.customer.repository.ConsultNoteRepository;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.FollowUpTaskRepository;
import jakarta.validation.Validator;
import java.time.LocalDate;
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
    private final FollowUpTaskRepository taskRepository;
    private final StaffNotifier notifier;
    private final Validator validator;

    @Transactional
    public NoteItem create(Long customerId, NoteCreateRequest request, CurrentAccount account) {
        requireWriter(account);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        List<FieldError> errors = new ArrayList<>(BodyValidation.violations(validator, request));
        if (request.type() == NoteType.CALL) {
            if (request.callResult() == null) {
                BodyValidation.addIfAbsent(errors, new FieldError("callResult", "연락 결과를 골라 주세요."));
            }
            if (request.channel() == null) {
                BodyValidation.addIfAbsent(errors, new FieldError("channel", "연락 방법을 골라 주세요."));
            }
        }
        Staff author = findAuthor(request.staffId(), account, errors);
        FollowUpKind reminderKind = reminderKind(request, errors);
        Staff receiver = reminderKind == null ? null : findReceiver(request.reminder(), author, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        ConsultNote note = noteRepository.save(ConsultNote.write(customer, author.getId(), account.id(),
                request.type(), request.callResult(), request.channel(), request.content(), now));
        LocalDate reminderDate = null;
        if (reminderKind != null) {
            // 날짜를 정하면 그날의 할 일이 생긴다 (CAL-10·11). 받는 사람은 고른 직원, 기본은 쓴 직원이다
            reminderDate = request.reminder().date();
            taskRepository.save(FollowUpTask.manual(customer, receiver.getId(), reminderKind, note.getId(),
                    reminderDate, now));
        }
        notifyOwner(customer, author, note);
        return NoteItem.from(note, author.getName(), canEdit(note, account, author), reminderDate, null);
    }

    /**
     * 담당 직원이 아닌 사람이 메모를 남기면 담당 직원 알림함에 알린다 (CAL-09). 문구에 메모 내용은 넣지 않는다.
     * 담당이 없거나 매장 계정이거나 본인이 쓴 메모면 알리지 않는다.
     */
    private void notifyOwner(Customer customer, Staff author, ConsultNote note) {
        Long ownerId = customer.getStaffId();
        if (ownerId == null || ownerId.equals(author.getId())) {
            return;
        }
        Staff owner = staffRepository.findById(ownerId).orElse(null);
        if (owner == null || !owner.isActive() || !EMPLOYEE_RANKS.contains(owner.getRole())) {
            return;
        }
        notifier.send(ownerId, NotificationType.NOTE, customer.getId(), note.getId(),
                author.getName() + "님이 " + customer.getName() + "님 메모를 남겼어요.");
    }

    /**
     * 메모의 날짜(reminder)가 어떤 할 일이 되는지 정한다 (CAL-10·11). 방문 예약(RESERVATION)은 날짜가 필수이고 방문 예정(VISIT)이 된다.
     * 연락(CALL) 결과가 재연락(RECALL)이면 날짜는 선택이고 다시 연락(RECALL) 할 일이 된다. 그 밖의 메모에는 날짜를 정할 수 없다.
     * 날짜는 오늘 이후여야 한다. 해당이 없으면 null.
     */
    private FollowUpKind reminderKind(NoteCreateRequest request, List<FieldError> errors) {
        NoteCreateRequest.Reminder reminder = request.reminder();
        boolean reservation = request.type() == NoteType.RESERVATION;
        boolean recall = request.type() == NoteType.CALL && request.callResult() == com.crmpos.customer.domain.CallResult.RECALL;
        if (reservation && (reminder == null || reminder.date() == null)) {
            errors.add(new FieldError("reminderDate", "방문 날짜를 골라 주세요."));
            return null;
        }
        if (reminder == null || reminder.date() == null) {
            return null;
        }
        if (!reservation && !recall) {
            errors.add(new FieldError("reminderDate", "방문 예약이나 재연락 메모에만 날짜를 정할 수 있습니다."));
            return null;
        }
        if (reminder.date().isBefore(LocalDate.now(KstTime.ZONE))) {
            errors.add(new FieldError("reminderDate", "오늘 이후 날짜를 골라 주세요."));
            return null;
        }
        return reservation ? FollowUpKind.VISIT : FollowUpKind.RECALL;
    }

    /** 할 일을 받을 직원: 고른 직원(작성 직원과 같은 매장의 활성 직원), 안 골랐으면 작성 직원. */
    private Staff findReceiver(NoteCreateRequest.Reminder reminder, Staff author, List<FieldError> errors) {
        if (reminder.staffId() == null || author == null || reminder.staffId().equals(author.getId())) {
            return author;
        }
        Staff staff = staffRepository.findById(reminder.staffId()).orElse(null);
        boolean sameStore = staff != null && staff.getStore() != null && author.getStore() != null
                && staff.getStore().getId().equals(author.getStore().getId());
        if (staff == null || !staff.isActive() || !sameStore || !EMPLOYEE_RANKS.contains(staff.getRole())) {
            errors.add(new FieldError("reminder.staffId", "이 매장의 활성 직원이 아닙니다."));
            return null;
        }
        return staff;
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
        List<Long> noteIds = result.getContent().stream().map(ConsultNote::getId).toList();
        Map<Long, LocalDate> reminderDates = new HashMap<>();
        Map<Long, Long> followUpIds = new HashMap<>();
        if (!noteIds.isEmpty()) {
            taskRepository.findBySourceNoteIdIn(noteIds).forEach(t -> reminderDates.put(t.getSourceNoteId(), t.getDueDate()));
            taskRepository.findByNoteIdIn(noteIds).forEach(t -> followUpIds.put(t.getNoteId(), t.getId()));
        }
        List<NoteItem> items = result.getContent().stream().map(n -> {
            Staff author = authors.get(n.getStaffId());
            return NoteItem.from(n, author == null ? null : author.getName(), canEdit(n, account, author),
                    reminderDates.get(n.getId()), followUpIds.get(n.getId()));
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
        LocalDate reminderDate = taskRepository.findBySourceNoteIdIn(List.of(noteId)).stream().findFirst()
                .map(FollowUpTask::getDueDate).orElse(null);
        Long followUpId = taskRepository.findByNoteIdIn(List.of(noteId)).stream().findFirst()
                .map(FollowUpTask::getId).orElse(null);
        return NoteItem.from(note, author == null ? null : author.getName(), true, reminderDate, followUpId);
    }

    /**
     * 메모를 지운다. 이 메모로 만든 방문 예약·재연락 할 일 중 아직 열린 것은 취소한다(이미 끝난 것은 그대로, CAL-10·11).
     * 날짜만 남은 할 일이 계속 알림을 내지 않게 하기 위해서다.
     */
    @Transactional
    public void delete(Long noteId, CurrentAccount account) {
        ConsultNote note = findEditable(noteId, account);
        LocalDateTime now = LocalDateTime.now(KstTime.ZONE).truncatedTo(ChronoUnit.MICROS);
        taskRepository.findBySourceNoteIdIn(List.of(noteId)).stream().filter(FollowUpTask::isOpen)
                .forEach(task -> task.cancel(now));
        noteRepository.delete(note);
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
