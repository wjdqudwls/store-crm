package com.crmpos.sales.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.Store;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.api.CustomerApi;
import com.crmpos.sales.controller.SaleDetailResponse;
import com.crmpos.sales.domain.Sale;
import com.crmpos.sales.domain.SaleLine;
import com.crmpos.sales.domain.SaleReturn;
import com.crmpos.sales.repository.SaleReturnRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 영수증 상세 응답을 만든다 (03-api.md 4.12). 이름은 다른 모듈(common·customer)에서 한 번에 읽는다. 트랜잭션 안에서 불러야 한다. */
@Component
@RequiredArgsConstructor
public class SaleDetailBuilder {

    private final SaleReturnRepository returnRepository;
    private final StaffRepository staffRepository;
    private final StoreRepository storeRepository;
    private final CustomerApi customerApi;

    public SaleDetailResponse build(Sale sale) {
        List<SaleReturn> returns = returnRepository.findBySaleId(sale.getId());
        Set<Long> staffIds = Stream.concat(Stream.of(sale.getStaffId()), returns.stream().map(SaleReturn::getStaffId))
                .collect(Collectors.toSet());
        Map<Long, String> staffNames = new HashMap<>();
        for (Staff staff : staffRepository.findAllById(staffIds)) {
            staffNames.put(staff.getId(), staff.getName());
        }
        Store store = storeRepository.findById(sale.getStoreId()).orElse(null);
        String customerName = sale.getCustomerId() == null ? null
                : customerApi.findNames(Set.of(sale.getCustomerId())).get(sale.getCustomerId());

        List<SaleDetailResponse.Line> lines = sale.getLines().stream().map(SaleDetailBuilder::line).toList();
        List<SaleDetailResponse.Return> returnItems = returns.stream().map(r -> new SaleDetailResponse.Return(
                r.getId(), r.getKind(), KstTime.toOffset(r.getReturnedAt()), staffNames.get(r.getStaffId()),
                r.getAmount(), r.getNewSale() == null ? null : r.getNewSale().getId(),
                r.getNewSale() == null ? null : r.getNewSale().getReceiptNo())).toList();
        return new SaleDetailResponse(sale.getId(), sale.getReceiptNo(), KstTime.toOffset(sale.getSoldAt()),
                store == null ? null : new SaleDetailResponse.StoreRef(store.getId(), store.getName()), sale.getKind(),
                sale.getStatus(), sale.getCustomerId(), customerName, sale.getStaffId(),
                staffNames.get(sale.getStaffId()), sale.getAppliedGrade(), lines, returnItems,
                sale.getOriginSale() == null ? null : sale.getOriginSale().getId(),
                sale.getOriginSale() == null ? null : sale.getOriginSale().getReceiptNo(), sale.getTotalQty(),
                sale.getTotalAmount());
    }

    private static SaleDetailResponse.Line line(SaleLine l) {
        return new SaleDetailResponse.Line(l.getId(), l.getSkuId(), l.getProductCode(), l.getProductName(),
                l.getItemType(), l.getColor() == null || l.getColor().isEmpty() ? null : l.getColor(), l.getSize(),
                l.getListPrice(), l.getQty(), l.getLineAmount(), l.getReturnedQty(), l.getReturnedAmount(),
                l.getWearerLabel());
    }
}
