package com.crmpos.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crmpos.common.repository.StaffRepository;
import com.crmpos.product.domain.Product;
import com.crmpos.product.repository.ProductRepository;
import com.crmpos.support.IntegrationTest;
import com.crmpos.support.TestAuth;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 상품 엑셀 폼 내려받기 → 채워 올리기 → 상품 생성, 틀린 행 목록 (PRD-02, ADR-015). 가상 데이터다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ProductImportIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StaffRepository staffRepository;

    private byte[] template() throws Exception {
        return mockMvc.perform(get("/api/v1/products/import-template")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** 폼을 내려받아 주어진 행(품번, 상품명, 품목, 대상, 정가, 색상, 사이즈)을 예시 아래에 채운다. */
    private byte[] filled(List<String[]> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template()));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheet("상품");
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(2 + i);
                for (int c = 0; c < rows.get(i).length; c++) {
                    if (!rows.get(i)[c].isEmpty()) {
                        row.createCell(c).setCellValue(rows.get(i)[c]);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ResultActions upload(byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/v1/products/import")
                .file(new MockMultipartFile("file", "products.xlsx", "application/octet-stream", bytes)));
    }

    private static String[] row(String code, String name, String item, String target, String price, String colors,
            String sizes) {
        return new String[] {code, name, item, target, price, colors, sizes};
    }

    @Test
    @DisplayName("폼에는 상품·작성 방법 시트, 7개 열, 품목·대상 드롭다운, 예시 1행이 있다")
    void should_provideTemplate_withDropdownsAndExample() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            Sheet sheet = wb.getSheet("상품");
            assertThat(wb.getSheet("작성 방법")).isNotNull();
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 7);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).startsWith("(예시)");
            assertThat(sheet.getDataValidations()).hasSize(2);
        }
    }

    @Test
    @DisplayName("폼을 채워 올리면 예시 행은 빼고 상품과 SKU가 만들어진다")
    void should_createProducts_when_templateFilled() throws Exception {
        upload(filled(List.<String[]>of(
                row("XL-001", "엑셀 코트", "아우터", "여성", "398,000", "블랙,네이비", "90, 95"),
                row("", "엑셀 티셔츠", "상의", "", "29000", "", "s,M"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importedCount").value(2));

        Product coat = productRepository.findAll().stream().filter(p -> "XL-001".equals(p.getProductCode())).findFirst()
                .orElseThrow();
        assertThat(coat.getListPrice()).isEqualTo(398000);
        assertThat(coat.getSkus()).hasSize(4);
        assertThat(productRepository.findAll()).extracting(Product::getName).contains("엑셀 티셔츠")
                .noneMatch(n -> n.startsWith("(예시)"));
    }

    @Test
    @DisplayName("틀린 행이 하나라도 있으면 PRODUCT_IMPORT_FAILED와 행 번호·열·이유를 주고 아무것도 저장하지 않는다")
    void should_saveNothing_andListRowErrors_when_anyRowWrong() throws Exception {
        long before = productRepository.count();

        upload(filled(List.<String[]>of(
                row("OK-1", "맞는 상품", "상의", "", "1000", "", "M"),
                row("", "", "바지", "어른", "-5", "", ""),
                row("", "사이즈 틀림", "하의", "여성", "1000", "", "95"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMPORT_FAILED"))
                .andExpect(jsonPath("$.message").value("엑셀에 잘못된 행이 있어 아무것도 저장하지 않았습니다."))
                .andExpect(jsonPath("$.details.errors[?(@.row==4 && @.column=='상품명')]").exists())
                .andExpect(jsonPath("$.details.errors[?(@.row==4 && @.column=='품목')]").exists())
                .andExpect(jsonPath("$.details.errors[?(@.row==4 && @.column=='대상')]").exists())
                .andExpect(jsonPath("$.details.errors[?(@.row==4 && @.column=='정가')]").exists())
                .andExpect(jsonPath("$.details.errors[?(@.row==4 && @.column=='사이즈')]").exists())
                .andExpect(jsonPath("$.details.errors[?(@.row==5)].reason").value("'95'는 여성 하의 사이즈가 아닙니다."));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("품번이 이미 등록됐거나 파일 안에서 겹치면(대소문자 무시) 행 오류다")
    void should_reportDuplicateCodes_inFileAndDb() throws Exception {
        upload(filled(List.<String[]>of(row("DUP-1", "먼저", "상의", "", "1000", "", "M")))).andExpect(status().isOk());

        upload(filled(List.<String[]>of(
                row("dup-1", "이미 있음", "상의", "", "1000", "", "M"),
                row("NEW-1", "새것", "상의", "", "1000", "", "M"),
                row("new-1", "파일 중복", "상의", "", "1000", "", "M"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.errors[?(@.row==3)].reason").value("이미 등록된 품번입니다."))
                .andExpect(jsonPath("$.details.errors[?(@.row==5)].column").value("품번"));
    }

    @Test
    @DisplayName("1,000행은 올라가고(30초 이내, NFR-02a) 1,001행은 400이다")
    void should_accept1000Rows_andReject1001() throws Exception {
        List<String[]> thousand = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(i -> row("BIG-" + i, "대량 상품 " + i, "상의", "", "10000", "블랙", "S,M,L")).toList();

        byte[] bytes = filled(thousand);
        long start = System.nanoTime();
        upload(bytes).andExpect(status().isOk()).andExpect(jsonPath("$.importedCount").value(1000));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("product import 1000 rows ms=" + millis);
        assertThat(millis).isLessThan(30_000);

        java.util.ArrayList<String[]> over = new java.util.ArrayList<String[]>(thousand);
        over.add(row("BIG-1000", "하나 더", "상의", "", "1000", "", "M"));
        upload(filled(over)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
    }

    @Test
    @DisplayName("엑셀이 아니거나 열 이름이 다르거나 행이 없거나 파일이 없으면 VALIDATION_FAILED")
    void should_return400_whenFileWrong() throws Exception {
        upload("그냥 글".getBytes()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
        upload(new byte[0]).andExpect(status().isBadRequest());
        upload(filled(List.<String[]>of())).andExpect(status().isBadRequest()) // 예시 행만 있음
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("상품").createRow(0).createCell(0).setCellValue("이름");
            wb.write(out);
            upload(out.toByteArray()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
        }
        mockMvc.perform(multipart("/api/v1/products/import")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
    }

    @Test
    @DisplayName("직원 계정은 올릴 수 없고(403) 폼은 받을 수 있다. 토큰이 없으면 401")
    void should_checkRoles() throws Exception {
        String junior = TestAuth.bearer(staffRepository.findByLoginId("gn01-junior").orElseThrow().getId());
        mockMvc.perform(multipart("/api/v1/products/import")
                        .file(new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, junior))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products/import-template").header(HttpHeaders.AUTHORIZATION, junior))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/products/import-template").header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isUnauthorized());
    }
}
