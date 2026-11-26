package com.crmpos.product.controller;

import com.crmpos.common.domain.StaffRole;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.security.CurrentAccount;
import com.crmpos.product.service.ProductImportService;
import com.crmpos.product.service.ProductTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 상품 엑셀 폼 내려받기와 올리기 (PRD-02, ADR-015). */
@Tag(name = "상품")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductImportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductTemplate template;
    private final ProductImportService importService;

    @Operation(summary = "상품 엑셀 폼", description = "로그인한 누구나. 품목·대상 드롭다운, 작성 예시 1행, 작성 방법 시트가 든 .xlsx.")
    @GetMapping(value = "/import-template", produces = XLSX)
    public ResponseEntity<byte[]> downloadTemplate(CurrentAccount account) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("product-import-template.xlsx").build().toString())
                .body(template.build());
    }

    @Operation(summary = "상품 엑셀 올리기", description = "매장 계정·본사. multipart의 file 하나(.xlsx, 최대 1,000행). 모두 맞으면 {importedCount} 200. 틀린 행이 있으면 아무것도 저장하지 않고 PRODUCT_IMPORT_FAILED 400과 details.errors[{row,column,reason}]. 파일이 아니거나 폼이 다르거나 1,000행을 넘으면 VALIDATION_FAILED 400(fieldErrors[file]).")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Integer> importProducts(CurrentAccount account, @RequestPart("file") MultipartFile file)
            throws IOException {
        account.requireRole(StaffRole.STORE, StaffRole.HQ);
        if (file.isEmpty()) {
            throw new ValidationFailedException(List.of(new FieldError("file", "파일을 올려 주세요.")));
        }
        try (InputStream in = file.getInputStream()) {
            return Map.of("importedCount", importService.importFrom(in));
        }
    }
}
