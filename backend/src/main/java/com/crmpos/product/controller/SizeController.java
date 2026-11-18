package com.crmpos.product.controller;

import com.crmpos.common.type.SizeRule;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 품목·대상별 허용 사이즈. 민감 정보가 없어 로그인 없이 연다 (03-api.md 4.12, REG-01). */
@RestController
public class SizeController {

    @GetMapping("/public/sizes")
    public List<SizeRule.SizeGroup> sizes() {
        return SizeRule.groups();
    }
}
