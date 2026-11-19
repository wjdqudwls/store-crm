package com.crmpos.customer.domain;

import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import java.util.List;
import lombok.Builder;

/**
 * 고객 등록에 쓰는 값 묶음. 서비스가 검증하고 전화번호·상세주소를 암호화한 뒤 만든다.
 * 이름·번호·주소가 들어 있어 toString은 값을 보이지 않는다 (NFR-04b).
 */
@Builder(builderClassName = "Builder", toBuilder = false)
public record CustomerDraft(
        String name, String nameEn, Nationality nationality,
        byte[] phoneEnc, String phoneHash, String phoneLast4,
        Short birthYear, String birthMd, String regionSido, String regionGu, byte[] addressDetailEnc,
        Gender gender, List<ItemType> preferredItemTypes,
        String sizeTop, String sizeOuter, String sizeBottom, String sizeShoes,
        ContactChannel contactChannel, Long homeStoreId, Long staffId, CustomerSource source,
        boolean privacyAgreed, boolean age14Confirmed, boolean marketingSms, boolean marketingKakao) {

    @Override
    public String toString() {
        return "CustomerDraft[source=" + source + ", homeStoreId=" + homeStoreId + ", 개인정보 가림]";
    }
}
