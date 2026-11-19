package com.crmpos.common.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL 경계 (ADR-018): resources/mappers/{모듈}/ 의 SQL은 자기 모듈 테이블만 쓴다.
 * 테이블 이름은 02-erd.md의 모듈별 표 기준이다. 다른 모듈 데이터는 api 창구나 이벤트로 받은 사본을 쓴다.
 */
class SqlBoundaryTest {

    private static final Path MAPPERS = Path.of("src/main/resources/mappers");

    static final Map<String, List<String>> TABLES = Map.of(
            "common", List.of("store", "staff", "staff_notification"),
            "product", List.of("product", "product_sku"),
            "sales", List.of("sale", "sale_line", "sale_return", "sale_return_line", "receipt_sequence",
                    "store_day_close", "outbox_event"),
            "customer", List.of("customer", "customer_member", "customer_purchase", "customer_summary",
                    "grade_policy", "grade_rule", "grade_discount", "grade_history", "consult_note",
                    "follow_up_rule", "follow_up_task", "processed_event"));

    /** 모듈 폴더의 SQL 파일에서 다른 모듈의 테이블 이름이 나온 곳을 찾는다. */
    static List<String> violations(Path moduleDir, String module, Map<String, List<String>> tables) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(moduleDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".xml")).toList()) {
                String sql = Files.readString(file).toLowerCase();
                for (var entry : tables.entrySet()) {
                    if (entry.getKey().equals(module)) {
                        continue;
                    }
                    for (String table : entry.getValue()) {
                        if (Pattern.compile("(?<![a-z0-9_])" + table + "(?![a-z0-9_])").matcher(sql).find()) {
                            found.add(file.getFileName() + " → " + table + " (" + entry.getKey() + " 모듈)");
                        }
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("실제 매퍼 SQL은 자기 모듈 테이블만 쓴다")
    void should_useOnlyOwnModuleTables() throws IOException {
        assertThat(MAPPERS).isDirectory();
        try (Stream<Path> dirs = Files.list(MAPPERS)) {
            for (Path moduleDir : dirs.filter(Files::isDirectory).toList()) {
                String module = moduleDir.getFileName().toString();
                assertThat(TABLES).containsKey(module);
                assertThat(violations(moduleDir, module, TABLES)).as(module + " 매퍼").isEmpty();
            }
        }
    }

    @Test
    @DisplayName("다른 모듈의 테이블(staff)을 조인하면 규칙이 실패한다")
    void should_detectOtherModuleTable(@org.junit.jupiter.api.io.TempDir Path temp) throws IOException {
        Path dir = Files.createDirectory(temp.resolve("customer"));
        Files.writeString(dir.resolve("Bad.xml"),
                "<select>SELECT c.id FROM customer c JOIN staff s ON s.id = c.staff_id</select>");

        assertThat(violations(dir, "customer", TABLES)).hasSize(1).first().asString().contains("staff");
    }

    @Test
    @DisplayName("자기 모듈 테이블이나 이름이 비슷한 칼럼(staff_id)은 위반이 아니다")
    void should_notFlagOwnTablesOrSimilarColumns(@org.junit.jupiter.api.io.TempDir Path temp) throws IOException {
        Path dir = Files.createDirectory(temp.resolve("customer"));
        Files.writeString(dir.resolve("Good.xml"),
                "<select>SELECT c.id, c.staff_id, s.customer_id FROM customer c JOIN customer_summary s</select>");

        assertThat(violations(dir, "customer", TABLES)).isEmpty();
    }
}
