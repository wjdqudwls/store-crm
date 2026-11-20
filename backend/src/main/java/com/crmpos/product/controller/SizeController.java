package com.crmpos.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.crmpos.common.type.SizeRule;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 품목·대상별 허용 사이즈. 민감 정보가 없어 로그인 없이 연다 (03-api.md 4.12, REG-01). */
@Tag(name = "상품")
@RestController
public class SizeController {

    @Operation(summary = "허용 사이즈 목록", description = "품목·대상별 허용 사이즈. 민감 정보가 없어 로그인 없이 공개한다(고객 등록 화면용).")
    @GetMapping("/public/sizes")
    public List<SizeRule.SizeGroup> sizes() {
        return SizeRule.groups();
    }
}
