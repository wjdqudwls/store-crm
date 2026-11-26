package com.crmpos.product.service;

import com.crmpos.common.error.BusinessException;
import com.crmpos.common.error.ErrorCode;
import com.crmpos.common.error.FieldError;
import com.crmpos.common.error.ValidationFailedException;
import com.crmpos.common.type.ItemType;
import com.crmpos.common.type.SizeRule;
import com.crmpos.common.type.Target;
import com.crmpos.product.domain.Product;
import com.crmpos.product.repository.ProductRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 엑셀 올리기 (PRD-02, ADR-015). 모든 행을 검사해 하나라도 틀리면 아무것도 저장하지 않고 행별 오류를 돌려주며
 * (행 번호는 엑셀에 보이는 번호), 모두 맞으면 한 트랜잭션으로 저장한다. 최대 1,000행.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final int MAX_LIST = 20;
    private static final long MAX_PRICE = 1_000_000_000_000L;

    private final ProductRepository productRepository;

    public record RowError(int row, String column, String reason) {
    }

    /** 검사를 통과한 한 행. */
    private record Parsed(int row, String code, String name, ItemType itemType, Target target, long price,
            List<String> colors, List<String> sizes) {
    }

    /** 저장한 상품 수를 돌려준다. */
    @Transactional
    public int importFrom(InputStream file) {
        List<RowError> errors = new ArrayList<>();
        List<Parsed> parsed = read(file, errors);
        checkDuplicateCodes(parsed, errors);
        if (!errors.isEmpty()) {
            errors.sort(java.util.Comparator.comparingInt(RowError::row));
            throw new BusinessException(ErrorCode.PRODUCT_IMPORT_FAILED, Map.of("errors", errors));
        }
        List<Product> products = parsed.stream().map(p -> {
            Product product = Product.create(p.code(), p.name(), p.itemType(), p.target(), p.price());
            product.replaceSkus(p.colors(), p.sizes());
            return product;
        }).toList();
        try {
            productRepository.saveAll(products);
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 올리는 사이에 같은 품번이 등록된 경우 UNIQUE가 막는다 (전부 되돌아간다)
            throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATED);
        }
        return products.size();
    }

    private List<Parsed> read(InputStream file, List<RowError> errors) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheet(ProductSheet.DATA_SHEET);
            if (sheet == null || !headerMatches(sheet.getRow(0))) {
                throw badFile("엑셀 폼의 시트·열 이름이 다릅니다. 폼을 내려받아 채워 주세요.");
            }
            List<Row> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || isBlank(row) || text(row, 1).startsWith(ProductSheet.EXAMPLE_PREFIX)) {
                    continue;
                }
                rows.add(row);
                if (rows.size() > ProductSheet.MAX_ROWS) {
                    throw badFile("한 번에 최대 " + ProductSheet.MAX_ROWS + "행까지 올릴 수 있습니다.");
                }
            }
            if (rows.isEmpty()) {
                throw badFile("등록할 상품 행이 없습니다.");
            }
            List<Parsed> parsed = new ArrayList<>();
            for (Row row : rows) {
                Parsed result = parse(row, errors);
                if (result != null) {
                    parsed.add(result);
                }
            }
            return parsed;
        } catch (ValidationFailedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // 엑셀이 아니거나 깨진 파일. 파일 내용은 로그에 남기지 않는다
            throw badFile("엑셀(.xlsx) 파일을 읽을 수 없습니다.");
        }
    }

    private static ValidationFailedException badFile(String reason) {
        return new ValidationFailedException(List.of(new FieldError("file", reason)));
    }

    private static boolean headerMatches(Row header) {
        if (header == null) {
            return false;
        }
        for (int i = 0; i < ProductSheet.HEADERS.size(); i++) {
            if (!ProductSheet.HEADERS.get(i).equals(text(header, i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(Row row) {
        for (int i = 0; i < ProductSheet.HEADERS.size(); i++) {
            if (!text(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 칸에 보이는 글. 숫자는 "95.0"이 아니라 "95"로, 수식은 저장된 결과 값으로 읽는다. */
    private static String text(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case STRING -> cell.getStringCellValue().strip();
            default -> new DataFormatter().formatCellValue(cell).strip();
        };
    }

    /** 틀린 칸이 있으면 오류를 모두 담고 null. */
    private static Parsed parse(Row row, List<RowError> errors) {
        int number = row.getRowNum() + 1;
        int before = errors.size();
        String code = text(row, 0);
        String name = text(row, 1);
        String itemText = text(row, 2);
        String targetText = text(row, 3);
        String priceText = text(row, 4).replace(",", "");
        List<String> colors = split(text(row, 5));
        List<String> sizes = split(text(row, 6)).stream().map(s -> s.toUpperCase(Locale.ROOT)).distinct().toList();

        if (code.length() > 20) {
            errors.add(new RowError(number, "품번", "20자까지 쓸 수 있습니다."));
        }
        if (name.isEmpty()) {
            errors.add(new RowError(number, "상품명", "필수 칸입니다."));
        } else if (name.length() > 100) {
            errors.add(new RowError(number, "상품명", "100자까지 쓸 수 있습니다."));
        }
        ItemType itemType = ProductSheet.ITEM_TYPES.get(itemText);
        if (itemText.isEmpty()) {
            errors.add(new RowError(number, "품목", "필수 칸입니다."));
        } else if (itemType == null) {
            errors.add(new RowError(number, "품목", "목록에 있는 값을 골라 주세요."));
        }
        Target target = ProductSheet.TARGETS.get(targetText);
        if (!targetText.isEmpty() && target == null) {
            errors.add(new RowError(number, "대상", "목록에 있는 값을 골라 주세요."));
        }
        long price = parsePrice(priceText);
        if (priceText.isEmpty()) {
            errors.add(new RowError(number, "정가", "필수 칸입니다."));
        } else if (price < 0) {
            errors.add(new RowError(number, "정가", "0 이상 정수를 써 주세요."));
        }
        if (colors.size() > MAX_LIST || colors.stream().anyMatch(c -> c.length() > 30)) {
            errors.add(new RowError(number, "색상", "색상은 " + MAX_LIST + "개까지, 각 30자까지 쓸 수 있습니다."));
        }
        if (sizes.isEmpty()) {
            errors.add(new RowError(number, "사이즈", "필수 칸입니다."));
        } else if (sizes.size() > MAX_LIST) {
            errors.add(new RowError(number, "사이즈", MAX_LIST + "개까지 쓸 수 있습니다."));
        } else if (itemType != null && (targetText.isEmpty() || target != null)) {
            for (String size : sizes) {
                if (!SizeRule.isAllowed(itemType, target, size)) {
                    String who = target == null ? "" : ProductSheet.targetLabel(target) + " ";
                    errors.add(new RowError(number, "사이즈",
                            "'" + size + "'는 " + who + ProductSheet.itemTypeLabel(itemType) + " 사이즈가 아닙니다."));
                }
            }
        }
        if (errors.size() > before) {
            return null;
        }
        return new Parsed(number, code.isEmpty() ? null : code, name, itemType, target, price, colors, sizes);
    }

    /** 0 이상 정수가 아니면 -1. */
    private static long parsePrice(String text) {
        try {
            long value = new BigDecimal(text).longValueExact();
            return value >= 0 && value <= MAX_PRICE ? value : -1;
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    private static List<String> split(String value) {
        return java.util.Arrays.stream(value.split("[,，]")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /** 품번은 DB에서 대소문자를 가리지 않으므로(utf8mb4 unicode_ci) 파일 안의 중복도 그렇게 본다. */
    private void checkDuplicateCodes(List<Parsed> parsed, List<RowError> errors) {
        Map<String, Integer> first = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (Parsed p : parsed) {
            if (p.code() != null) {
                String key = p.code().toLowerCase(Locale.ROOT);
                if (first.putIfAbsent(key, p.row()) != null) {
                    errors.add(new RowError(p.row(), "품번", "파일 안에 같은 품번이 있습니다 (" + first.get(key) + "행)."));
                }
                codes.add(p.code());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        Set<String> existing = new HashSet<>();
        productRepository.findExistingCodes(codes).forEach(c -> existing.add(c.toLowerCase(Locale.ROOT)));
        for (Parsed p : parsed) {
            if (p.code() != null && existing.contains(p.code().toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(p.row(), "품번", "이미 등록된 품번입니다."));
            }
        }
    }
}
