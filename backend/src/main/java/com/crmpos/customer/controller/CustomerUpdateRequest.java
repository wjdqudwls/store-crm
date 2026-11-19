package com.crmpos.customer.controller;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.Nationality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 고객 수정 본문 (03-api.md 3.3, CUS-04). 등록과 같은 칸을 전체 교체하고, 동의 대신 마케팅 수신({@code marketing})을 보낸다.
 * 이름·전화번호·주소가 들어 있어 toString은 값을 보이지 않는다 (NFR-04b).
 */
public record CustomerUpdateRequest(
        @Size(max = 50) String name,
        @Size(max = 100) String nameEn,
        @NotBlank @Size(max = 30) String phone,
        Nationality nationality,
        Integer birthYear,
        @Size(max = 4) String birthMd,
        @Valid CustomerRegisterRequest.Address address,
        Gender gender,
        @Size(max = 5) List<ItemType> preferredItemTypes,
        @Valid CustomerRegisterRequest.Sizes sizes,
        ContactChannel contactChannel,
        Long staffId,
        @NotNull @Valid Marketing marketing) {

    public record Marketing(Boolean sms, Boolean kakao) {
    }

    @Override
    public String toString() {
        return "CustomerUpdateRequest[개인정보 가림]";
    }
}
