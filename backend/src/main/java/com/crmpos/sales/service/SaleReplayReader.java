package com.crmpos.sales.service;

import com.crmpos.sales.controller.SaleResponse;
import com.crmpos.sales.repository.SaleRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 같은 요청 키로 다시 온 요청에 저장된 판매로 처음과 같은 응답을 만든다 (ADR-006). */
@Component
@RequiredArgsConstructor
public class SaleReplayReader {

    /** 저장된 판매의 응답과 그때의 요청 본문 해시. */
    public record Stored(SaleResponse response, String requestHash) {
    }

    private final SaleRepository saleRepository;

    @Transactional(readOnly = true)
    public Optional<Stored> findByKey(String idempotencyKey) {
        return saleRepository.findByIdempotencyKey(idempotencyKey)
                .map(sale -> new Stored(SaleResponse.from(sale), sale.getRequestHash()));
    }
}
