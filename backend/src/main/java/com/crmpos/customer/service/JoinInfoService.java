package com.crmpos.customer.service;

import com.crmpos.common.domain.Store;
import com.crmpos.common.type.SizeRule;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** QR·키오스크 등록 폼이 처음 받는 정보 (REG-02·03): 매장 이름, 동의 문구, 사이즈 선택지. 로그인 없이 열리므로 개인정보가 없다. */
@Service
@RequiredArgsConstructor
public class JoinInfoService {

    /** 동의 문구 (BR-08, 화면설계 U1). 문구를 바꿀 때는 이 값만 고친다. */
    static final Consents CONSENTS = new Consents(
            "[개인정보 수집·이용 동의 (필수)]\n"
                    + "· 수집 목적: 단골 관리, 구매 이력·등급 할인 안내, 사이즈 상담, 연락\n"
                    + "· 수집 항목: 필수(이름, 전화번호) / 선택(생년, 생일, 주소, 성별, 선호 품목, 사이즈, 연락 채널, 가족 구성원 정보)\n"
                    + "· 상세주소는 사은품·우편 발송에만 쓰고 암호화해 보관합니다.\n"
                    + "· 동의하지 않으면 등록과 등급 할인을 받을 수 없습니다.",
            "만 14세 이상입니다. (필수)",
            "[마케팅 수신 동의 (선택)]\n"
                    + "신상품·행사 소식을 문자 또는 카카오톡으로 받는 것에 동의합니다. 동의하지 않아도 등록과 등급 할인은 받을 수 있습니다.");

    private final PublicRegistrationService publicRegistrationService;

    public record Consents(String privacy, String age14, String marketing) {
    }

    public record JoinInfo(StoreInfo store, Consents consents, List<SizeRule.SizeGroup> sizes) {
    }

    public record StoreInfo(String code, String name) {
    }

    /** 없는(또는 닫힌) 매장 코드는 404 STORE_NOT_FOUND. 직원 코드가 맞는지는 알려 주지 않는다. */
    public JoinInfo get(String storeCode) {
        Store store = publicRegistrationService.findActiveStore(storeCode == null ? "" : storeCode);
        return new JoinInfo(new StoreInfo(store.getCode(), store.getName()), CONSENTS, SizeRule.groups());
    }
}
