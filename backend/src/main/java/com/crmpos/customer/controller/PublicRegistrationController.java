package com.crmpos.customer.controller;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.web.RequestRateLimiter;
import com.crmpos.customer.service.JoinInfoService;
import com.crmpos.customer.service.PublicRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR·키오스크 고객 등록 (REG-01·02·03·05, NFR-04a). 로그인 없는 공개 API는 요청 수를 제한한다(IP당 분당 5번). IP는
 * 서버가 믿는 프록시가 정한 원격 주소(prod는 {@code server.forward-headers-strategy=native})이고, 헤더 값을 그대로 믿지 않는다.
 * 신규든 기존 번호든 응답이 같다. 본문에 개인정보가 있어 {@code @Valid}를 쓰지 않고 서비스가 오류를 모은다.
 */
@Tag(name = "고객 등록(QR·키오스크)")
@RestController
@RequiredArgsConstructor
public class PublicRegistrationController {

    static final Map<String, String> DONE = Map.of("message", "등록이 완료되었습니다.");

    private final PublicRegistrationService registrationService;
    private final JoinInfoService joinInfoService;
    private final RequestRateLimiter publicRegistrationLimiter;

    @Operation(summary = "QR 첫 정보", description = "로그인 없이 연다. 매장 이름·동의 문구·사이즈 선택지를 준다. 없는 매장 코드는 STORE_NOT_FOUND 404. 직원 코드(staff)가 맞는지는 알려 주지 않는다.")
    @GetMapping("/public/join-info")
    public JoinInfoService.JoinInfo joinInfo(@RequestParam(name = "store", required = false) String store,
            @RequestParam(name = "staff", required = false) String staff) {
        return joinInfoService.get(store);
    }

    @Operation(summary = "QR 등록", description = "로그인 없이 고객이 직접 등록한다. IP당 1분에 5번을 넘으면 TOO_MANY_REQUESTS 429. 새 번호든 기존 번호든 같은 200 응답이고 기존 고객 정보는 바꾸지 않는다. 직원 코드가 틀려도 등록은 받고 담당 직원만 비운다. 없는 매장 코드는 STORE_NOT_FOUND 404, 형식 오류는 fieldErrors와 함께 400.")
    @PostMapping("/public/registrations")
    public Map<String, String> registerByQr(HttpServletRequest http, @RequestBody PublicRegisterRequest request) {
        if (!publicRegistrationLimiter.tryAcquire(http.getRemoteAddr())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        registrationService.registerByQr(request);
        return DONE;
    }

    @Operation(summary = "키오스크 등록", description = "매장 iPad(KIOSK 계정)에서 고객이 직접 등록한다. 가입매장은 토큰의 매장이고 담당 직원은 비운다. 새 번호든 기존 번호든 같은 200 응답이다. KIOSK가 아니면 ACCESS_DENIED 403.")
    @PostMapping("/kiosk/registrations")
    public Map<String, String> registerByKiosk(CurrentAccount account, @RequestBody PublicRegisterRequest request) {
        account.requireRole(StaffRole.KIOSK);
        registrationService.registerByKiosk(request, account);
        return DONE;
    }
}
