package com.crmpos.sales.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 영수증 번호 = 매장코드-판매일자-순번4자리 (예: GN01-20261015-0012). 순번이 9999를 넘으면 자릿수만 늘어난다. */
public final class ReceiptNumber {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private ReceiptNumber() {
    }

    public static String format(String storeCode, LocalDate saleDate, int sequence) {
        return storeCode + "-" + DATE.format(saleDate) + "-" + String.format("%04d", sequence);
    }
}
