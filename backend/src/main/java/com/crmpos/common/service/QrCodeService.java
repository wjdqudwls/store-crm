package com.crmpos.common.service;

import com.crmpos.common.domain.Staff;
import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.domain.Store;
import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.common.security.CurrentAccount;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장·직원 QR 이미지 (REG-03, ADM-01·03). QR에는 고객 등록 폼 주소({@code {base}/join?store=GN01&staff=S017})가 담긴다.
 * 본사만 받을 수 있다. base는 {@code crm.public.base-url}(환경변수 PUBLIC_BASE_URL)이다.
 */
@Service
public class QrCodeService {

    private static final int SIZE = 512;
    private static final Set<StaffRole> STAFF_RANKS = Set.of(StaffRole.MANAGER, StaffRole.SENIOR, StaffRole.JUNIOR, StaffRole.STAFF);

    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;
    private final String baseUrl;

    public QrCodeService(StoreRepository storeRepository, StaffRepository staffRepository,
            @Value("${crm.public.base-url:http://localhost:8080}") String baseUrl) {
        this.storeRepository = storeRepository;
        this.staffRepository = staffRepository;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional(readOnly = true)
    public byte[] storeQr(Long storeId, CurrentAccount account) {
        account.requireRole(StaffRole.HQ);
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        return png(joinUrl(store.getCode(), null));
    }

    /** 직원 코드가 있는 직원 직급 계정만 QR이 있다. 없거나 비활성이면 404 STAFF_NOT_FOUND. */
    @Transactional(readOnly = true)
    public byte[] staffQr(Long staffId, CurrentAccount account) {
        account.requireRole(StaffRole.HQ);
        Staff staff = staffRepository.findById(staffId)
                .filter(s -> s.isActive() && STAFF_RANKS.contains(s.getRole()) && s.getStaffCode() != null && s.getStore() != null)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        return png(joinUrl(staff.getStore().getCode(), staff.getStaffCode()));
    }

    String joinUrl(String storeCode, String staffCode) {
        String url = baseUrl + "/join?store=" + enc(storeCode);
        return staffCode == null ? url : url + "&staff=" + enc(staffCode);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] png(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("QR 이미지를 만들지 못했다", e);
        }
    }
}
