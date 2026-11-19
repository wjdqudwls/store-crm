package com.crmpos.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.common.type.GradeCode;
import com.crmpos.customer.domain.Customer;
import com.crmpos.customer.domain.CustomerDraft;
import com.crmpos.customer.domain.CustomerSource;
import com.crmpos.customer.domain.CustomerSummary;
import com.crmpos.customer.domain.Nationality;
import com.crmpos.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis 검색 SQL을 실제 MariaDB로 확인한다. 가상 이름만 쓴다. */
@IntegrationTest
@Transactional
class CustomerQueryMapperTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    CustomerQueryMapper mapper;
    @Autowired
    CustomerRepository customerRepository;
    @Autowired
    CustomerSummaryRepository summaryRepository;

    private Customer save(String name, String last4, Long staffId) {
        String hash = String.format("%064d", SEQ.incrementAndGet());
        CustomerDraft draft = CustomerDraft.builder().name(name).nationality(Nationality.DOMESTIC)
                .phoneEnc(new byte[] {1}).phoneHash(hash).phoneLast4(last4).staffId(staffId)
                .privacyAgreed(true).age14Confirmed(true).homeStoreId(1L).source(CustomerSource.STORE).build();
        LocalDateTime now = LocalDateTime.of(2026, 11, 19, 10, 0);
        Customer customer = customerRepository.saveAndFlush(Customer.register(draft, now));
        summaryRepository.saveAndFlush(CustomerSummary.initial(customer.getId(), 1, now));
        return customer;
    }

    @Test
    @DisplayName("이름의 일부로 찾고 이름 순으로 돌려주며 뒷자리 4개와 등급이 함께 나온다")
    void should_searchByPartialName() {
        save("검색테스트 김하늘", "4821", 7L);
        save("검색테스트 김하나", "0937", null);
        save("검색테스트 박도윤", "1111", null);

        List<CustomerListRow> rows = mapper.searchByName("김하", 20, 0);

        assertThat(rows).extracting(CustomerListRow::name).containsExactly("검색테스트 김하나", "검색테스트 김하늘");
        assertThat(rows.get(1).phoneLast4()).isEqualTo("4821");
        assertThat(rows.get(1).grade()).isEqualTo(GradeCode.NORMAL);
        assertThat(rows.get(1).staffId()).isEqualTo(7L);
        assertThat(rows.get(0).staffId()).isNull();
        assertThat(rows.get(0).lastVisitAt()).isNull();
        assertThat(mapper.countByName("김하")).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 이름이면 빈 목록이고 개수는 0이다")
    void should_returnEmpty_when_nameNotFound() {
        assertThat(mapper.searchByName("없는이름없는이름", 20, 0)).isEmpty();
        assertThat(mapper.countByName("없는이름없는이름")).isZero();
    }

    @Test
    @DisplayName("limit와 offset으로 나눠 읽고 전체 개수는 그대로다")
    void should_page() {
        save("페이지테스트 가", "0001", null);
        save("페이지테스트 나", "0002", null);
        save("페이지테스트 다", "0003", null);

        List<CustomerListRow> first = mapper.searchByName("페이지테스트", 2, 0);
        List<CustomerListRow> second = mapper.searchByName("페이지테스트", 2, 2);

        assertThat(first).extracting(CustomerListRow::name).containsExactly("페이지테스트 가", "페이지테스트 나");
        assertThat(second).extracting(CustomerListRow::name).containsExactly("페이지테스트 다");
        assertThat(mapper.countByName("페이지테스트")).isEqualTo(3);
    }

    @Test
    @DisplayName("검색어의 퍼센트·밑줄은 글자 그대로 찾는다 (서비스가 !로 막은 값)")
    void should_matchWildcardsLiterally() {
        save("와일드 50%할인", "0001", null);
        save("와일드 50번", "0002", null);

        assertThat(mapper.searchByName("50!%", 20, 0)).extracting(CustomerListRow::name)
                .containsExactly("와일드 50%할인");
    }

    @Test
    @DisplayName("목록 줄의 toString은 이름·번호를 보이지 않는다")
    void should_maskPersonalData_when_rowToString() {
        CustomerListRow row = new CustomerListRow(1L, "김하늘", "4821", GradeCode.B, null, null);

        assertThat(row.toString()).doesNotContain("김하늘").doesNotContain("4821");
    }
}
