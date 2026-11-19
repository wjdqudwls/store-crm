package com.crmpos.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.sales.controller.ReturnRequest;
import com.crmpos.sales.controller.ReturnResponse;
import com.crmpos.sales.domain.SaleStatus;
import com.crmpos.sales.repository.SaleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ReturnRecordingServiceTest {

    private static final String KEY = "3d8a2f4b-6c1e-4b7a-8e2d-5f9c0a1b2c3d";
    private static final CurrentAccount STORE = new CurrentAccount(3L, StaffRole.STORE, 1L);

    @Mock
    ReturnWriter returnWriter;
    @Mock
    StaffRepository staffRepository;
    @Mock
    SaleRepository saleRepository;

    ReturnRecordingService service;

    @BeforeEach
    void setUp() {
        service = new ReturnRecordingService(returnWriter, staffRepository, saleRepository, JsonMapper.builder().build());
        lenient().when(saleRepository.findStoreId(88231L)).thenReturn(Optional.of(1L));
        Staff staff = mock(Staff.class);
        Store store = mock(Store.class);
        lenient().when(store.getId()).thenReturn(1L);
        lenient().when(staff.getStore()).thenReturn(store);
        lenient().when(staff.getRole()).thenReturn(StaffRole.SENIOR);
        lenient().when(staff.isActive()).thenReturn(true);
        lenient().when(staffRepository.findById(17L)).thenReturn(Optional.of(staff));
    }

    private static ReturnRequest request(long staffId, ReturnRequest.Line... lines) {
        return new ReturnRequest(staffId, List.of(lines));
    }

    private static ReturnRequest.Line line(long lineId, int qty) {
        return new ReturnRequest.Line(lineId, qty);
    }

    private static ReturnResponse response() {
        return new ReturnResponse(5021L, 217800, SaleStatus.PARTIAL_RETURNED);
    }

    @Test
    @DisplayName("매장 계정이 아니면 403 STORE_REQUIRED (본사도 반품은 하지 않는다)")
    void should_throwStoreRequired_when_notStoreAccount() {
        for (StaffRole role : List.of(StaffRole.HQ, StaffRole.STAFF, StaffRole.KIOSK)) {
            assertThatThrownBy(() -> service.record(88231L, KEY, request(17L, line(2, 1)),
                    new CurrentAccount(5L, role, 1L)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORE_REQUIRED));
        }
        verify(returnWriter, never()).record(any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("요청 키가 없거나 UUID가 아니면 400 fieldErrors[Idempotency-Key]")
    void should_return400_when_keyInvalid() {
        for (String key : new String[] {null, "", "abc"}) {
            assertThatThrownBy(() -> service.record(88231L, key, request(17L, line(2, 1)), STORE))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                            assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                    .containsExactly("Idempotency-Key"));
        }
    }

    @Test
    @DisplayName("같은 줄을 두 번 고르면 400 fieldErrors[lines[1].lineId]")
    void should_return400_when_sameLineTwice() {
        assertThatThrownBy(() -> service.record(88231L, KEY, request(17L, line(2, 1), line(2, 1)), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactly("lines[1].lineId"));
    }

    @Test
    @DisplayName("처리 직원이 다른 매장·비활성이면 400, 없으면 404")
    void should_checkProcessingStaff() {
        Staff other = mock(Staff.class);
        Store otherStore = mock(Store.class);
        lenient().when(otherStore.getId()).thenReturn(99L);
        lenient().when(other.getStore()).thenReturn(otherStore);
        lenient().when(other.getRole()).thenReturn(StaffRole.STAFF);
        lenient().when(other.isActive()).thenReturn(true);
        given(staffRepository.findById(5L)).willReturn(Optional.of(other));
        given(staffRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(88231L, KEY, request(5L, line(2, 1)), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("staffId"));
        assertThatThrownBy(() -> service.record(88231L, KEY, request(404L, line(2, 1)), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STAFF_NOT_FOUND));
    }

    @Test
    @DisplayName("처음 저장하면 created=true")
    void should_returnCreated_whenStoredFirstTime() {
        ReturnRequest body = request(17L, line(2, 1));
        String hash = service.requestHash(body, 88231L, 1L);
        given(returnWriter.record(eq(88231L), eq(1L), eq(3L), eq(KEY), eq(hash), eq(body)))
                .willReturn(new ReturnWriter.Stored(response(), hash, true));

        ReturnRecordingService.Result result = service.record(88231L, KEY, body, STORE);

        assertThat(result.created()).isTrue();
        assertThat(result.response().returnedAmount()).isEqualTo(217800);
    }

    @Test
    @DisplayName("같은 키·같은 내용이면 저장된 반품으로 created=false, 내용이 다르면 409")
    void should_replayOrReject_byHash() {
        ReturnRequest body = request(17L, line(2, 1));
        String hash = service.requestHash(body, 88231L, 1L);
        given(returnWriter.record(any(), any(), any(), anyString(), anyString(), any()))
                .willReturn(new ReturnWriter.Stored(response(), hash, false))
                .willReturn(new ReturnWriter.Stored(response(), "x".repeat(64), false));

        assertThat(service.record(88231L, KEY, body, STORE).created()).isFalse();
        assertThatThrownBy(() -> service.record(88231L, KEY, body, STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("같은 키가 다른 판매에 쓰이면 해시가 달라 409 (판매 ID가 해시에 들어 있다)")
    void should_hashDiffer_acrossSales() {
        ReturnRequest body = request(17L, line(2, 1));

        assertThat(service.requestHash(body, 1L, 1L)).isNotEqualTo(service.requestHash(body, 2L, 1L));
        assertThat(service.requestHash(body, 1L, 1L)).isNotEqualTo(service.requestHash(body, 1L, 2L));
        assertThat(service.requestHash(body, 1L, 1L)).hasSize(64);
    }

    @Test
    @DisplayName("동시에 같은 키가 UNIQUE에 걸리면 먼저 저장된 반품으로 응답한다")
    void should_replayStored_whenUniqueViolation() {
        ReturnRequest body = request(17L, line(2, 1));
        String hash = service.requestHash(body, 88231L, 1L);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(returnWriter)
                .record(any(), any(), any(), anyString(), anyString(), any());
        given(returnWriter.findByKey(KEY)).willReturn(Optional.of(new ReturnWriter.Stored(response(), hash, false)));

        ReturnRecordingService.Result result = service.record(88231L, KEY, body, STORE);

        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("UNIQUE에 걸렸는데 저장된 반품이 없으면 원래 오류를 던진다")
    void should_rethrow_whenNothingStored() {
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("other")).when(returnWriter)
                .record(any(), any(), any(), anyString(), anyString(), any());
        given(returnWriter.findByKey(KEY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(88231L, KEY, request(17L, line(2, 1)), STORE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("권한이 입력 검사보다 먼저다: 다른 매장의 판매는 요청 키·직원이 틀려도 403, 없는 판매는 404")
    void should_checkPermissionBeforeInput() {
        given(saleRepository.findStoreId(777L)).willReturn(Optional.of(99L));
        given(saleRepository.findStoreId(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(777L, KEY, request(5L, line(2, 1), line(2, 1)), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.record(404L, KEY, request(17L, line(2, 1)), STORE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SALE_NOT_FOUND));
        verify(returnWriter, never()).record(any(), any(), any(), anyString(), anyString(), any());
    }
}
