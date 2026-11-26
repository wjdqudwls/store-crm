package com.crmpos.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.common.repository.StoreRepository;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 본사가 받는 매장·직원 QR 이미지 (REG-03): PNG이고, 읽으면 등록 폼 주소가 나온다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class QrIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    StaffRepository staffRepository;
    @Autowired
    StoreRepository storeRepository;

    private String hq() {
        return TestAuth.bearer(staffRepository.findByLoginId("hq").orElseThrow().getId());
    }

    /** PNG를 QR로 읽어 담긴 글자를 돌려준다. */
    private static String decode(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
    }

    @Test
    @DisplayName("본사는 매장 QR을 PNG로 받고 읽으면 매장 코드가 든 등록 폼 주소다")
    void should_returnStoreQr_toHq() throws Exception {
        long storeId = storeRepository.findByCode("GN01").orElseThrow().getId();

        byte[] png = mockMvc.perform(get("/api/v1/stores/" + storeId + "/qr").header(HttpHeaders.AUTHORIZATION, hq()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(decode(png)).isEqualTo("http://localhost:8080/join?store=GN01");
    }

    @Test
    @DisplayName("본사는 직원 QR을 받고 읽으면 매장 코드와 직원 코드가 든다")
    void should_returnStaffQr_toHq() throws Exception {
        long staffId = staffRepository.findByLoginId("gn01-senior").orElseThrow().getId();

        byte[] png = mockMvc.perform(get("/api/v1/staff/" + staffId + "/qr").header(HttpHeaders.AUTHORIZATION, hq()))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(decode(png)).isEqualTo("http://localhost:8080/join?store=GN01&staff=S002");
    }

    @Test
    @DisplayName("본사가 아니면(매장 계정·직원) 403이고 이미지를 주지 않는다")
    void should_return403_whenNotHq() throws Exception {
        long storeId = storeRepository.findByCode("GN01").orElseThrow().getId();
        long staffId = staffRepository.findByLoginId("gn01-senior").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/stores/" + storeId + "/qr")) // 기본 토큰 = 강남점 매장 계정
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/staff/" + staffId + "/qr").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(staffId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 매장은 404 STORE_NOT_FOUND, 직원 코드가 없는 계정(매장 계정)은 404 STAFF_NOT_FOUND")
    void should_return404_whenNothingToDraw() throws Exception {
        long storeAccountId = staffRepository.findByLoginId("store-gn01").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/stores/999999/qr").header(HttpHeaders.AUTHORIZATION, hq()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/staff/" + storeAccountId + "/qr").header(HttpHeaders.AUTHORIZATION, hq()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/staff/999999/qr").header(HttpHeaders.AUTHORIZATION, hq()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void should_return401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/stores/1/qr").header(HttpHeaders.AUTHORIZATION, "")).andExpect(status().isUnauthorized());
    }
}
