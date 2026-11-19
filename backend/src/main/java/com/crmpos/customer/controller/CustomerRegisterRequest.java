package com.crmpos.customer.controller;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.customer.domain.ContactChannel;
import com.crmpos.customer.domain.Nationality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 고객 등록 본문 (03-api.md 4.5, REG-01). QR·키오스크·직원 대리 등록이 같은 본문을 쓴다.
 * 이름·전화번호·주소가 들어 있어 toString은 값을 보이지 않는다 (NFR-04b).
 */
public record CustomerRegisterRequest(
        @Size(max = 50) String name,
        @Size(max = 100) String nameEn,
        @NotBlank @Size(max = 30) String phone,
        Nationality nationality,
        @NotNull @Valid Agreements agreements,
        Integer birthYear,
        @Size(max = 4) String birthMd,
        @Valid Address address,
        Gender gender,
        @Size(max = 5) List<ItemType> preferredItemTypes,
        @Valid Sizes sizes,
        ContactChannel contactChannel,
        Long staffId) {

    public record Agreements(
            @NotNull @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다.") Boolean privacy,
            @NotNull @AssertTrue(message = "만 14세 이상임을 확인해야 합니다.") Boolean age14,
            Boolean marketingSms,
            Boolean marketingKakao) {

        @Override
        public String toString() {
            return "Agreements[동의 내용 가림]";
        }
    }

    public record Address(@Size(max = 20) String sido, @Size(max = 20) String gu, @Size(max = 200) String detail) {

        @Override
        public String toString() {
            return "Address[개인정보 가림]";
        }
    }

    public record Sizes(@Size(max = 20) String top, @Size(max = 20) String outer, @Size(max = 20) String bottom,
            @Size(max = 20) String shoes) {
    }

    @Override
    public String toString() {
        return "CustomerRegisterRequest[개인정보 가림]";
    }
}
