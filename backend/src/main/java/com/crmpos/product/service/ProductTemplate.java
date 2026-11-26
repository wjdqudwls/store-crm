package com.crmpos.product.service;

import com.crmpos.common.type.SizeRule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** 상품 엑셀 폼(.xlsx) 만들기 (PRD-02): 품목·대상 드롭다운, 작성 예시 1행, 작성 방법 시트. */
@Component
public class ProductTemplate {

    public byte[] build() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Sheet data = workbook.createSheet(ProductSheet.DATA_SHEET);
            Row head = data.createRow(0);
            for (int i = 0; i < ProductSheet.HEADERS.size(); i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(ProductSheet.HEADERS.get(i));
                cell.setCellStyle(header);
                data.setColumnWidth(i, 18 * 256);
            }
            // 품번·색상·사이즈는 글자 칸으로 (앞의 0이나 "90,95"가 숫자로 바뀌지 않게)
            data.setDefaultColumnStyle(0, text);
            data.setDefaultColumnStyle(5, text);
            data.setDefaultColumnStyle(6, text);

            Row example = data.createRow(1);
            example.createCell(0).setCellValue("D2110");
            example.createCell(1).setCellValue(ProductSheet.EXAMPLE_PREFIX + " 울 블렌드 롱코트");
            example.createCell(2).setCellValue("아우터");
            example.createCell(3).setCellValue("여성");
            example.createCell(4).setCellValue(798000);
            example.createCell(5).setCellValue("차콜,베이지");
            example.createCell(6).setCellValue("90,95");
            example.getCell(0).setCellStyle(text);
            example.getCell(5).setCellStyle(text);
            example.getCell(6).setCellStyle(text);

            dropdown(data, 2, ProductSheet.ITEM_TYPES.keySet().toArray(String[]::new));
            dropdown(data, 3, ProductSheet.TARGETS.keySet().toArray(String[]::new));
            data.createFreezePane(0, 1);

            guide(workbook, header);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void dropdown(Sheet sheet, int column, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation validation = helper.createValidation(helper.createExplicitListConstraint(values),
                new CellRangeAddressList(1, ProductSheet.MAX_ROWS, column, column));
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private static void guide(XSSFWorkbook workbook, CellStyle header) {
        Sheet guide = workbook.createSheet(ProductSheet.GUIDE_SHEET);
        guide.setColumnWidth(0, 14 * 256);
        guide.setColumnWidth(1, 90 * 256);
        List<String[]> rows = new java.util.ArrayList<>(List.of(
                new String[] {"칸", "적는 법"},
                new String[] {"품번", "선택. 최대 20자. 같은 품번은 한 번만 등록할 수 있다"},
                new String[] {"상품명", "필수. 최대 100자"},
                new String[] {"품목", "필수. 목록에서 고른다: " + String.join(", ", ProductSheet.ITEM_TYPES.keySet())},
                new String[] {"대상", "선택. 목록에서 고른다: " + String.join(", ", ProductSheet.TARGETS.keySet())},
                new String[] {"정가", "필수. 0 이상 정수 (원)"},
                new String[] {"색상", "선택. 여러 개면 쉼표로 (예: 블랙,네이비)"},
                new String[] {"사이즈", "필수. 여러 개면 쉼표로 (예: S,M,L). 품목·대상에 맞는 값만 쓴다"},
                new String[] {"", "한 행이 한 상품이다. 한 번에 최대 " + ProductSheet.MAX_ROWS + "행. 하나라도 틀리면 아무것도 저장하지 않는다"},
                new String[] {"", "상품명이 '" + ProductSheet.EXAMPLE_PREFIX + "'로 시작하는 행은 예시라 저장하지 않는다"},
                new String[] {"", ""},
                new String[] {"품목·대상", "쓸 수 있는 사이즈"}));
        for (SizeRule.SizeGroup group : SizeRule.groups()) {
            String label = ProductSheet.itemTypeLabel(group.itemType())
                    + (group.target() == null ? "" : " " + ProductSheet.targetLabel(group.target()));
            rows.add(new String[] {label, String.join(", ", group.sizes())});
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = guide.createRow(i);
            for (int c = 0; c < 2; c++) {
                row.createCell(c).setCellValue(rows.get(i)[c]);
                if (i == 0 || "품목·대상".equals(rows.get(i)[0])) {
                    row.getCell(c).setCellStyle(header);
                }
            }
        }
    }
}
