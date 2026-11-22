package com.crmpos.common.controller;

import com.crmpos.common.security.CurrentAccount;
import com.crmpos.common.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 본사가 내려받는 매장·직원 QR 이미지 (PNG, REG-03). */
@Tag(name = "QR")
@RestController
@RequiredArgsConstructor
public class QrController {

    private final QrCodeService service;

    @Operation(summary = "매장 QR 이미지", description = "본사만. 고객 등록 폼 주소(매장 코드)가 담긴 PNG. 그 외 ACCESS_DENIED 403, 없는 매장 STORE_NOT_FOUND 404.")
    @GetMapping(value = "/stores/{storeId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> storeQr(CurrentAccount account, @PathVariable Long storeId) {
        return png(service.storeQr(storeId, account));
    }

    @Operation(summary = "직원 QR 이미지", description = "본사만. 매장 코드와 직원 코드가 담긴 PNG. 직원 코드가 없는 계정·비활성 직원은 STAFF_NOT_FOUND 404, 그 외 ACCESS_DENIED 403.")
    @GetMapping(value = "/staff/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> staffQr(CurrentAccount account, @PathVariable Long id) {
        return png(service.staffQr(id, account));
    }

    private static ResponseEntity<byte[]> png(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header("Cache-Control", "no-store").body(bytes);
    }
}
