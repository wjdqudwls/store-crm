package com.crmpos.customer.controller;

import com.crmpos.common.type.GradeCode;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.Gender;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.Nationality;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 고객 상세 (03-api.md 4.4). 이름·전화번호·주소가 들어 있어 toString은 값을 보이지 않는다 (NFR-04b).
 * 상세주소는 {@code include=address}일 때만 들어간다.
 */
public record CustomerDetailResponse(
        Long id, String name, String nameEn, Nationality nationality, String phone, OffsetDateTime createdAt,
        Ref homeStore, Ref staff, Grade grade, Stats stats, Profile profile, Marketing marketing,
        @JsonInclude(JsonInclude.Include.NON_NULL) String addressDetail) {

    public record Ref(Long id, String name) {
    }

    public record Grade(GradeCode current, GradeCode computed, GradeCode confirmed, String confirmedStatus,
            String season, int seasonTicketCount, int ticketScore, long seasonNetAmount, int amountScore,
            int totalScore, int discountRate, int policyVersion, ConfirmedBasis confirmedBasis) {
    }

    public record ConfirmedBasis(String season, int ticketCount, long netAmount, int totalScore) {
    }

    public record Stats(OffsetDateTime firstVisitAt, OffsetDateTime lastVisitAt, int seasonQty,
            long lifetimeAmount) {
    }

    public record Profile(Gender gender, Short birthYear, String birthMd, String regionSido, String regionGu,
            List<ItemType> preferredItemTypes, Sizes sizes, ContactChannel contactChannel) {
    }

    public record Sizes(String top, String outer, String bottom, String shoes) {
    }

    public record Marketing(boolean sms, boolean kakao) {
    }

    @Override
    public String toString() {
        return "CustomerDetailResponse[id=" + id + "]";
    }
}
