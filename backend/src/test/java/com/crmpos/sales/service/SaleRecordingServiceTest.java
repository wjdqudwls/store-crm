package com.crmpos.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.customer.api.CustomerApi;
import com.crmpos.customer.api.SaleCustomer;
import com.crmpos.product.api.ProductApi;
import com.crmpos.product.api.SkuInfo;
import com.crmpos.sales.controller.SaleRequest;
import com.crmpos.sales.controller.SaleResponse;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleKind;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SaleRecordingServiceTest {

    private static final String KEY = "6b0f6c1e-2f7a-4f3c-9a51-1d2c3e4f5a6b";
    private static final CurrentAccount STORE = new CurrentAccount(3L, StaffRole.STORE, 1L);

    @Mock
    SaleWriter saleWriter;
    @Mock
    SaleReplayReader replayReader;
    @Mock
    StaffRepository staffRepository;
    @Mock
    StoreRepository storeRepository;
    @Mock
    ProductApi productApi;
    @Mock
    CustomerApi customerApi;

    SaleRecordingService service;

    @BeforeEach
    void setUp() {
        service = new SaleRecordingService(saleWriter, replayReader, staffRepository, storeRepository, productApi,
                customerApi, JsonMapper.builder().build(), false);
        lenient().when(replayReader.findByKey(any())).thenReturn(Optional.empty());
        Staff seller = staff(17L, StaffRole.SENIOR, 1L, true);
        lenient().when(staffRepository.findById(17L)).thenReturn(Optional.of(seller));
        Store store = mock(Store.class);
        lenient().when(store.getId()).thenReturn(1L);
        lenient().when(store.getCode()).thenReturn("GN01");
        lenient().when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        lenient().when(saleWriter.insert(any())).thenAnswer(i -> {
            NewSale n = i.getArgument(0);
            Sale sale = Sale.record(n.storeId(), "GN01-20261119-0001", n.idempotencyKey(), n.requestHash(),
                    n.sellerStaffId(), n.recordedBy(), n.customerId(), n.appliedGrade(), n.soldAt(),
                    LocalDateTime.now(), n.lines());
            org.springframework.test.util.ReflectionTestUtils.setField(sale, "id", 88231L);
            return sale;
        });
    }

    private static Staff staff(long id, StaffRole role, Long storeId, boolean active) {
        Staff staff = mock(Staff.class);
        lenient().when(staff.getId()).thenReturn(id);
        lenient().when(staff.getRole()).thenReturn(role);
        lenient().when(staff.isActive()).thenReturn(active);
        Store store = mock(Store.class);
        lenient().when(store.getId()).thenReturn(storeId);
        lenient().when(staff.getStore()).thenReturn(store);
        return staff;
    }

    private static SaleRequest.Line direct() {
        return new SaleRequest.Line(null, "캐시미어 머플러", ItemType.ACC, null, "FREE", "오트밀", 1, 217800L, null);
    }

    private static SaleRequest.Line sku(long skuId, long amount) {
        return new SaleRequest.Line(skuId, null, null, null, null, null, 1, amount, null);
    }

    private static SaleRequest request(Long customerId, String memo, SaleRequest.Line... lines) {
        return new SaleRequest(17L, customerId, List.of(lines), memo, null);
    }

    private static SkuInfo skuInfo() {
        return new SkuInfo(5501L, 301L, "D2110", "울 블렌드 롱코트", ItemType.OUTER, Target.WOMEN, "차콜", "90", 798000);
    }

    private static void assertCode(Throwable t, ErrorCode code) {
        assertThat(t).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    // ---- 권한·키

    @Test
    @DisplayName("매장 계정이 아니면 403 STORE_REQUIRED")
    void should_throwStoreRequired_when_notStoreAccount() {
        for (StaffRole role : List.of(StaffRole.HQ, StaffRole.STAFF, StaffRole.KIOSK)) {
            assertThatThrownBy(() -> service.record(KEY, request(null, null, direct()),
                    new CurrentAccount(5L, role, 1L))).satisfies(t -> assertCode(t, ErrorCode.STORE_REQUIRED));
        }
        verify(saleWriter, never()).insert(any());
    }

    @Test
    @DisplayName("요청 키가 없거나 UUID 형식이 아니면 400 fieldErrors[Idempotency-Key]")
    void should_return400_when_keyMissingOrMalformed() {
        for (String key : new String[] {null, "", "not-a-uuid", "6b0f6c1e-2f7a-4f3c-9a51"}) {
            assertThatThrownBy(() -> service.record(key, request(null, null, direct()), STORE))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                            assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                    .containsExactly("Idempotency-Key"));
        }
    }

    // ---- 중복 요청

    @Test
    @DisplayName("같은 키·같은 내용으로 다시 오면 새로 저장하지 않고 저장된 판매를 created=false로 돌려준다")
    void should_replay_when_sameKeySameBody() {
        SaleRequest body = request(null, null, direct());
        String hash = service.requestHash(body, 1L);
        SaleResponse stored = mock(SaleResponse.class);
        given(replayReader.findByKey(KEY)).willReturn(Optional.of(new SaleReplayReader.Stored(stored, hash)));

        SaleRecordingService.Result result = service.record(KEY, body, STORE);

        assertThat(result.created()).isFalse();
        assertThat(result.sale()).isSameAs(stored);
        verify(saleWriter, never()).insert(any());
    }

    @Test
    @DisplayName("같은 키인데 내용이 다르면 409 IDEMPOTENCY_KEY_REUSED")
    void should_throw409_when_sameKeyDifferentBody() {
        SaleRequest first = request(null, null, direct());
        SaleRequest second = request(null, null, sku(5501, 1000));
        given(replayReader.findByKey(KEY)).willReturn(
                Optional.of(new SaleReplayReader.Stored(mock(SaleResponse.class), service.requestHash(first, 1L))));

        assertThatThrownBy(() -> service.record(KEY, second, STORE))
                .satisfies(t -> assertCode(t, ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("다른 매장이 같은 키로 보내도 내용 해시가 달라 409")
    void should_throw409_when_sameKeyFromOtherStore() {
        SaleRequest body = request(null, null, direct());
        given(replayReader.findByKey(KEY)).willReturn(
                Optional.of(new SaleReplayReader.Stored(mock(SaleResponse.class), service.requestHash(body, 99L))));

        assertThatThrownBy(() -> service.record(KEY, body, STORE))
                .satisfies(t -> assertCode(t, ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("동시에 같은 키가 와서 UNIQUE에 걸리면 먼저 저장된 판매로 응답한다")
    void should_replayStored_when_uniqueViolation() {
        SaleRequest body = request(null, null, direct());
        SaleResponse stored = mock(SaleResponse.class);
        given(replayReader.findByKey(KEY)).willReturn(Optional.empty())
                .willReturn(Optional.of(new SaleReplayReader.Stored(stored, service.requestHash(body, 1L))));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(saleWriter).insert(any());

        SaleRecordingService.Result result = service.record(KEY, body, STORE);

        assertThat(result.created()).isFalse();
        assertThat(result.sale()).isSameAs(stored);
    }

    @Test
    @DisplayName("UNIQUE에 걸렸는데 저장된 판매가 없으면 원래 오류를 그대로 던진다")
    void should_rethrow_when_uniqueViolationWithoutStoredSale() {
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("other")).when(saleWriter).insert(any());

        assertThatThrownBy(() -> service.record(KEY, request(null, null, direct()), STORE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- 판매 사원

    @Test
    @DisplayName("판매 사원: 없으면 404, 다른 매장·비활성·매장 계정이면 400 fieldErrors[sellerStaffId]")
    void should_checkSeller() {
        Staff otherStore = staff(5L, StaffRole.STAFF, 99L, true);
        Staff inactive = staff(6L, StaffRole.STAFF, 1L, false);
        Staff storeAccount = staff(7L, StaffRole.STORE, 1L, true);
        given(staffRepository.findById(404L)).willReturn(Optional.empty());
        given(staffRepository.findById(5L)).willReturn(Optional.of(otherStore));
        given(staffRepository.findById(6L)).willReturn(Optional.of(inactive));
        given(staffRepository.findById(7L)).willReturn(Optional.of(storeAccount));

        assertThatThrownBy(() -> service.record(KEY, new SaleRequest(404L, null, List.of(direct()), null, null), STORE))
                .satisfies(t -> assertCode(t, ErrorCode.STAFF_NOT_FOUND));
        for (long id : new long[] {5L, 6L, 7L}) {
            assertThatThrownBy(() -> service.record(KEY, new SaleRequest(id, null, List.of(direct()), null, null), STORE))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                            assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                    .containsExactly("sellerStaffId"));
        }
        verify(saleWriter, never()).insert(any());
    }

    // ---- 라인

    @Test
    @DisplayName("라인은 등록 상품 또는 직접 입력 중 하나다: 둘 다/둘 다 없음은 400")
    void should_return400_when_lineHasBothOrNeither() {
        SaleRequest.Line both = new SaleRequest.Line(5501L, "머플러", ItemType.ACC, null, "FREE", null, 1, 1000L, null);
        SaleRequest.Line neither = new SaleRequest.Line(null, null, null, null, null, null, 1, 1000L, null);

        assertThatThrownBy(() -> service.record(KEY, request(null, null, both), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("lines[0]"));
        assertThatThrownBy(() -> service.record(KEY, request(null, null, neither), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactlyInAnyOrder("lines[0].productName", "lines[0].itemType", "lines[0].size"));
    }

    @Test
    @DisplayName("직접 입력의 사이즈가 품목에 없으면 400 INVALID_SIZE")
    void should_throwInvalidSize_when_directSizeNotAllowed() {
        SaleRequest.Line bad = new SaleRequest.Line(null, "코트", ItemType.OUTER, Target.WOMEN, "110", null, 1, 1000L, null);

        assertThatThrownBy(() -> service.record(KEY, request(null, null, bad), STORE))
                .satisfies(t -> assertCode(t, ErrorCode.INVALID_SIZE));
    }

    @Test
    @DisplayName("없는 skuId면 404 PRODUCT_NOT_FOUND")
    void should_throw404_when_skuMissing() {
        given(productApi.findSkus(any())).willReturn(Map.of());

        assertThatThrownBy(() -> service.record(KEY, request(null, null, sku(9999, 1000)), STORE))
                .satisfies(t -> assertCode(t, ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("등록 상품 라인은 상품 정보·정가를 복사하고 결제 금액은 받은 값 그대로 기록한다")
    void should_snapshotSkuAndKeepAmount() {
        given(productApi.findSkus(any())).willReturn(Map.of(5501L, skuInfo()));

        SaleRecordingService.Result result = service.record(KEY, request(null, null, sku(5501, 774060)), STORE);

        assertThat(result.created()).isTrue();
        SaleResponse.Line line = result.sale().lines().get(0);
        assertThat(line.productCode()).isEqualTo("D2110");
        assertThat(line.productName()).isEqualTo("울 블렌드 롱코트");
        assertThat(line.color()).isEqualTo("차콜");
        assertThat(line.listPrice()).isEqualTo(798000L);
        assertThat(line.lineAmount()).isEqualTo(774060L);
        assertThat(result.sale().kind()).isEqualTo(SaleKind.NORMAL);
        assertThat(result.sale().appliedGrade()).isNull();
    }

    @Test
    @DisplayName("직접 입력 라인은 skuId·품번·정가가 없다")
    void should_keepDirectLineWithoutSku() {
        SaleRecordingService.Result result = service.record(KEY, request(null, null, direct()), STORE);

        SaleResponse.Line line = result.sale().lines().get(0);
        assertThat(line.skuId()).isNull();
        assertThat(line.productCode()).isNull();
        assertThat(line.listPrice()).isNull();
        assertThat(line.productName()).isEqualTo("캐시미어 머플러");
    }

    // ---- 고객·착용자·메모

    @Test
    @DisplayName("회원 판매는 판매 당시 등급을 남기고 착용자 호칭을 복사한다")
    void should_storeAppliedGradeAndWearerLabel() {
        given(customerApi.findForSale(1024L)).willReturn(Optional.of(new SaleCustomer(1024L, GradeCode.B)));
        given(customerApi.findMemberNicknames(1024L, java.util.Set.of(31L))).willReturn(Map.of(31L, "남편"));
        SaleRequest.Line withWearer = new SaleRequest.Line(null, "머플러", ItemType.ACC, null, "FREE", null, 1, 217800L, 31L);

        SaleRecordingService.Result result = service.record(KEY, request(1024L, "메모", withWearer), STORE);

        assertThat(result.sale().appliedGrade()).isEqualTo(GradeCode.B);
        assertThat(result.sale().customerId()).isEqualTo(1024L);
        assertThat(result.sale().lines().get(0).wearerLabel()).isEqualTo("남편");
        ArgumentCaptor<NewSale> captor = ArgumentCaptor.forClass(NewSale.class);
        verify(saleWriter).insert(captor.capture());
        assertThat(captor.getValue().memo()).isEqualTo("메모");
        assertThat(captor.getValue().recordedBy()).isEqualTo(3L);
        assertThat(captor.getValue().sellerStaffId()).isEqualTo(17L);
    }

    @Test
    @DisplayName("없는 고객은 404 CUSTOMER_NOT_FOUND, 그 고객의 구성원이 아닌 착용자는 404 MEMBER_NOT_FOUND")
    void should_throw404_when_customerOrMemberMissing() {
        given(customerApi.findForSale(7L)).willReturn(Optional.empty());
        given(customerApi.findForSale(1024L)).willReturn(Optional.of(new SaleCustomer(1024L, GradeCode.NORMAL)));
        given(customerApi.findMemberNicknames(any(), any())).willReturn(Map.of());
        SaleRequest.Line withWearer = new SaleRequest.Line(null, "머플러", ItemType.ACC, null, "FREE", null, 1, 1000L, 99L);

        assertThatThrownBy(() -> service.record(KEY, request(7L, null, direct()), STORE))
                .satisfies(t -> assertCode(t, ErrorCode.CUSTOMER_NOT_FOUND));
        assertThatThrownBy(() -> service.record(KEY, request(1024L, null, withWearer), STORE))
                .satisfies(t -> assertCode(t, ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("비회원 판매에 메모나 착용자가 있으면 400")
    void should_return400_when_guestHasMemoOrWearer() {
        SaleRequest.Line withWearer = new SaleRequest.Line(null, "머플러", ItemType.ACC, null, "FREE", null, 1, 1000L, 31L);

        assertThatThrownBy(() -> service.record(KEY, request(null, "메모", direct()), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field).containsExactly("memo"));
        assertThatThrownBy(() -> service.record(KEY, request(null, null, withWearer), STORE))
                .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(FieldError::field)
                                .containsExactly("lines[0].memberId"));
    }

    // ---- 판매 시각

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 14, 3);

    private static OffsetDateTime kst(LocalDateTime local) {
        return local.atOffset(ZoneOffset.ofHours(9));
    }

    @Test
    @DisplayName("판매 시각을 생략하면 지금이다")
    void should_useNow_when_soldAtMissing() {
        List<FieldError> errors = new ArrayList<>();

        assertThat(SaleRecordingService.resolveSoldAt(null, NOW, false, errors)).isEqualTo(NOW);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("오늘 0시부터 지금까지는 받는다 (경계: 0시 정각 O, 어제 23:59 X)")
    void should_acceptTodayEarlierTimes() {
        List<FieldError> errors = new ArrayList<>();

        assertThat(SaleRecordingService.resolveSoldAt(kst(NOW.toLocalDate().atStartOfDay()), NOW, false, errors))
                .isEqualTo(NOW.toLocalDate().atStartOfDay());
        assertThat(errors).isEmpty();
        SaleRecordingService.resolveSoldAt(kst(NOW.toLocalDate().atStartOfDay().minusMinutes(1)), NOW, false, errors);
        assertThat(errors).extracting(FieldError::field).containsExactly("soldAt");
    }

    @Test
    @DisplayName("미래 시각은 거절하고, 시계 차이 1분 안은 지금으로 맞춰 받는다")
    void should_rejectFuture_butTolerateClockSkew() {
        List<FieldError> errors = new ArrayList<>();

        assertThat(SaleRecordingService.resolveSoldAt(kst(NOW.plusSeconds(30)), NOW, false, errors)).isEqualTo(NOW);
        assertThat(errors).isEmpty();
        SaleRecordingService.resolveSoldAt(kst(NOW.plusMinutes(5)), NOW, false, errors);
        assertThat(errors).extracting(FieldError::field).containsExactly("soldAt");
    }

    @Test
    @DisplayName("과거 날짜는 seed 설정이 켜졌을 때만 받고, 미래는 그때도 거절한다")
    void should_acceptPastOnlyWhenAllowed() {
        List<FieldError> errors = new ArrayList<>();
        LocalDateTime lastYear = NOW.minusYears(1);

        assertThat(SaleRecordingService.resolveSoldAt(kst(lastYear), NOW, true, errors)).isEqualTo(lastYear);
        assertThat(errors).isEmpty();
        SaleRecordingService.resolveSoldAt(kst(NOW.plusDays(1)), NOW, true, errors);
        assertThat(errors).hasSize(1);
    }

    @Test
    @DisplayName("다른 시간대(Z)로 보내도 한국 시간으로 바꿔 판단한다")
    void should_convertOffsetToKst() {
        List<FieldError> errors = new ArrayList<>();
        OffsetDateTime utc = NOW.minusHours(1).atOffset(ZoneOffset.ofHours(9)).withOffsetSameInstant(ZoneOffset.UTC);

        assertThat(SaleRecordingService.resolveSoldAt(utc, NOW, false, errors)).isEqualTo(NOW.minusHours(1));
    }

    @Test
    @DisplayName("요청 record의 toString은 메모를 보이지 않는다")
    void should_maskMemo_inToString() {
        assertThat(request(1024L, "개인적인 응대 메모", direct()).toString()).doesNotContain("응대 메모");
    }

    @Test
    @DisplayName("판매 시각은 DB(마이크로초)가 저장하는 정밀도로 맞춘다 (리눅스는 나노초라 처음 응답과 재전송 응답이 달라지는 것을 막는다)")
    void should_truncateSoldAtToMicros() {
        List<FieldError> errors = new ArrayList<>();
        LocalDateTime nowWithNanos = LocalDateTime.of(2026, 11, 19, 14, 3, 0, 123_456_789);
        LocalDateTime requested = LocalDateTime.of(2026, 11, 19, 13, 0, 0, 987_654_321);

        LocalDateTime fromNow = SaleRecordingService.resolveSoldAt(null, nowWithNanos, false, errors);
        LocalDateTime fromRequest = SaleRecordingService.resolveSoldAt(kst(requested), nowWithNanos, false, errors);

        assertThat(fromNow.getNano()).isEqualTo(123_456_000);
        assertThat(fromRequest.getNano()).isEqualTo(987_654_000);
        assertThat(errors).isEmpty();
    }
}
