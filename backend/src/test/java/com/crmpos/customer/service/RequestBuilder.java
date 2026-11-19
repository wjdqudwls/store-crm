package com.crmpos.customer.service;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.customer.controller.CustomerRegisterRequest;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.Nationality;
import java.util.List;

/** 테스트용 요청 만들기. 기본값은 정상 등록 가능한 내국인이다 (가상 데이터). */
class RequestBuilder {

    private String name = "김하늘";
    private String nameEn;
    private String phone = "010-2943-4821";
    private Nationality nationality = Nationality.DOMESTIC;
    private CustomerRegisterRequest.Agreements agreements = new CustomerRegisterRequest.Agreements(true, true, false, false);
    private Integer birthYear;
    private String birthMd;
    private CustomerRegisterRequest.Address address;
    private Gender gender;
    private List<ItemType> preferredItemTypes;
    private CustomerRegisterRequest.Sizes sizes;
    private ContactChannel contactChannel;
    private Long staffId;

    RequestBuilder name(String v) { this.name = v; return this; }
    RequestBuilder nameEn(String v) { this.nameEn = v; return this; }
    RequestBuilder phone(String v) { this.phone = v; return this; }
    RequestBuilder nationality(Nationality v) { this.nationality = v; return this; }
    RequestBuilder agreements(CustomerRegisterRequest.Agreements v) { this.agreements = v; return this; }
    RequestBuilder birthYear(Integer v) { this.birthYear = v; return this; }
    RequestBuilder address(CustomerRegisterRequest.Address v) { this.address = v; return this; }
    RequestBuilder gender(Gender v) { this.gender = v; return this; }
    RequestBuilder sizes(CustomerRegisterRequest.Sizes v) { this.sizes = v; return this; }
    RequestBuilder staffId(Long v) { this.staffId = v; return this; }

    CustomerRegisterRequest build() {
        return new CustomerRegisterRequest(name, nameEn, phone, nationality, agreements, birthYear, birthMd, address,
                gender, preferredItemTypes, sizes, contactChannel, staffId);
    }
}
