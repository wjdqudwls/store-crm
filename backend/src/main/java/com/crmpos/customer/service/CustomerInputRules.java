package com.crmpos.customer.service;

import com.crmpos.common.error.FieldError;
import com.crmpos.common.type.Gender;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.type.Target;
import com.crmpos.common.web.KstTime;
import com.crmpos.customer.domain.Nationality;
import java.time.Month;
import java.time.Year;
import java.util.List;

/** 고객 등록·수정이 같이 쓰는 입력 검사 (REG-01·06, CUS-04). 오류에는 필드 이름과 이유만 담고 입력값은 담지 않는다. */
final class CustomerInputRules {

    private CustomerInputRules() {
    }

    static void checkNames(String name, String nameEn, Nationality nationality, List<FieldError> errors) {
        boolean hasName = name != null && !name.isBlank();
        boolean hasNameEn = nameEn != null && !nameEn.isBlank();
        if (nationality == Nationality.FOREIGN) {
            if (!hasNameEn) {
                errors.add(new FieldError("nameEn", "외국인은 영문 이름이 필요합니다."));
            }
        } else {
            if (!hasName) {
                errors.add(new FieldError("name", "이름이 필요합니다."));
            }
            if (hasNameEn) {
                errors.add(new FieldError("nameEn", "내국인은 영문 이름을 쓰지 않습니다."));
            }
        }
    }

    static void checkBirth(Integer year, String md, List<FieldError> errors) {
        if (year != null && (year < 1900 || year > Year.now(KstTime.ZONE).getValue())) {
            errors.add(new FieldError("birthYear", "태어난 해를 확인해 주세요."));
        }
        if (md != null && !md.isBlank() && !validMonthDay(md)) {
            errors.add(new FieldError("birthMd", "생일은 월일 4자리(MMDD)로 적어 주세요."));
        }
    }

    /** 0229는 윤년에만 있는 날짜지만 연도와 따로 받으므로 허용한다. */
    static boolean validMonthDay(String md) {
        if (!md.matches("\\d{4}")) {
            return false;
        }
        int month = Integer.parseInt(md.substring(0, 2));
        int day = Integer.parseInt(md.substring(2));
        if (month < 1 || month > 12 || day < 1) {
            return false;
        }
        return day <= Month.of(month).length(true);
    }

    /** 성별 F는 여성, M은 남성 목록, 고르지 않으면 그 품목의 모든 허용값 (02-erd.md 3.2). */
    static void checkSizes(Gender gender, String top, String outer, String bottom, String shoes,
            List<FieldError> errors) {
        Target target = gender == null ? null : (gender == Gender.F ? Target.WOMEN : Target.MEN);
        checkSize("sizes.top", ItemType.TOP, target, top, errors);
        checkSize("sizes.outer", ItemType.OUTER, target, outer, errors);
        checkSize("sizes.bottom", ItemType.BOTTOM, target, bottom, errors);
        checkSize("sizes.shoes", ItemType.SHOES, target, shoes, errors);
    }

    private static void checkSize(String field, ItemType item, Target target, String size, List<FieldError> errors) {
        if (size != null && !size.isBlank() && !SizeRule.isAllowed(item, target, size.trim())) {
            errors.add(new FieldError(field, "이 품목의 사이즈 목록에 없습니다."));
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
