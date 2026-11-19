package com.crmpos.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Target;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerPurchase;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.ProcessedEvent;
import com.crmpos.customer.event.SaleCompletedEvent;
import com.crmpos.customer.event.SaleReturnedEvent;
import com.crmpos.customer.repository.ConsultNoteRepository;
import com.crmpos.customer.repository.CustomerPurchaseRepository;
import com.crmpos.customer.repository.CustomerRepository;
import com.crmpos.customer.repository.CustomerSummaryRepository;
import com.crmpos.customer.repository.ProcessedEventRepository;
import com.crmpos.customer.repository.VisitRange;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaleEventServiceTest {

    @Mock
    ProcessedEventRepository processedEventRepository;
    @Mock
    CustomerRepository customerRepository;
    @Mock
    CustomerSummaryRepository summaryRepository;
    @Mock
    CustomerPurchaseRepository purchaseRepository;
    @Mock
    ConsultNoteRepository noteRepository;
    @InjectMocks
    SaleEventService service;

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-19T14:03:00+09:00");

    private SaleCompletedEvent completed(Long customerId, String kind) {
        return new SaleCompletedEvent(AT, 100L, 1L, customerId, 7L, 3L, kind, null, 100L, null,
                List.of(new SaleCompletedEvent.Line(1L, Target.WOMEN, ItemType.OUTER, 1, 774060L)));
    }

    @BeforeEach
    void setUp() {
        given(summaryRepository.findById(5L)).willReturn(Optional.of(CustomerSummary.initial(5L, 1, LocalDateTime.now())));
        given(purchaseRepository.findVisitRange(5L)).willReturn(new VisitRange(null, null));
    }

    @Test
    @DisplayName("이미 처리한 이벤트는 아무것도 하지 않는다")
    void should_skip_when_alreadyProcessed() {
        given(processedEventRepository.existsById("e1")).willReturn(true);

        service.handleCompleted("e1", "SALE_COMPLETED", completed(5L, "NORMAL"));

        verify(processedEventRepository, never()).save(any());
        verifyNoInteractions(purchaseRepository, customerRepository);
    }

    @Test
    @DisplayName("비회원 판매는 처리 기록만 남기고 건너뛴다")
    void should_recordOnly_when_nonMember() {
        service.handleCompleted("e2", "SALE_COMPLETED", completed(null, "NORMAL"));

        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verifyNoInteractions(purchaseRepository, customerRepository, noteRepository);
    }

    @Test
    @DisplayName("없는 고객이면 예외를 던져 발행기가 되돌리고 다시 시도하게 한다")
    void should_throw_when_customerMissing() {
        given(customerRepository.findById(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleCompleted("e3", "SALE_COMPLETED", completed(5L, "NORMAL")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("교환 판매는 같은 구매 건의 처음 판매 시각을 실적 시각으로 쓴다")
    void should_useGroupCountedAt_when_exchange() {
        LocalDateTime first = LocalDateTime.of(2026, 6, 2, 11, 0);
        Customer customer = mock(Customer.class);
        given(customer.getId()).willReturn(5L);
        given(customerRepository.findById(5L)).willReturn(Optional.of(customer));
        given(purchaseRepository.findBySaleLineId(1L)).willReturn(Optional.empty());
        given(purchaseRepository.findCountedAtOfGroup(100L)).willReturn(Optional.of(first));

        service.handleCompleted("e4", "SALE_COMPLETED", completed(5L, "EXCHANGE"));

        org.mockito.ArgumentCaptor<CustomerPurchase> captor = org.mockito.ArgumentCaptor.forClass(CustomerPurchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertThat(captor.getValue().getCountedAt()).isEqualTo(first);
        assertThat(captor.getValue().getSoldAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 14, 3));
    }

    @Test
    @DisplayName("반품할 구매 기록이 없으면 예외를 던진다")
    void should_throw_when_returnedLineMissing() {
        given(purchaseRepository.findBySaleLineId(9L)).willReturn(Optional.empty());
        SaleReturnedEvent event = new SaleReturnedEvent(AT, 100L, 1L, "RETURN", 5L,
                List.of(new SaleReturnedEvent.Line(9L, 1, 100L)));

        assertThatThrownBy(() -> service.handleReturned("e5", "SALE_RETURNED", event))
                .isInstanceOf(IllegalStateException.class);
    }
}
