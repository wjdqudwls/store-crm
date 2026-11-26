package com.crmpos.customer.controller;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.Nationality;
import java.util.List;

/**
 * QR·키오스크 등록 본문 (03-api.md 4.5, REG-01·02·03). 직원 등록과 같은 칸에 매장 코드·직원 코드가 더해진다(키오스크는
 * 둘 다 보내지 않고 매장은 토큰에서 정한다). 이름·번호·주소가 들어 있어 toString은 값을 보이지 않는다 (NFR-04b).
 */
public record PublicRegisterRequest(
        String storeCode,
        String staffCode,
        String name,
        String nameEn,
        String phone,
        Nationality nationality,
        CustomerRegisterRequest.Agreements agreements,
        Integer birthYear,
        String birthMd,
        CustomerRegisterRequest.Address address,
        Gender gender,
        List<ItemType> preferredItemTypes,
        CustomerRegisterRequest.Sizes sizes,
        ContactChannel contactChannel) {

    /** 직원 등록과 같은 본문으로 바꿔 같은 검사를 쓴다. 담당 직원은 부르는 쪽이 따로 정한다. */
    public CustomerRegisterRequest toCustomerRequest() {
        return new CustomerRegisterRequest(name, nameEn, phone, nationality, agreements, birthYear, birthMd, address,
                gender, preferredItemTypes, sizes, contactChannel, null);
    }

    @Override
    public String toString() {
        return "PublicRegisterRequest[개인정보 가림]";
    }
}
