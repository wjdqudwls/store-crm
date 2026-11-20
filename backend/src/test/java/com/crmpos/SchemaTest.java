package com.crmpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway 마이그레이션(V1__baseline.sql)이 02-erd.md의 테이블을 모두 만드는지 확인한다 (B0-5, B0-7).
 * 테이블 수: common 3 + product 2 + sales 7 + customer 12 = 24 (02-erd.md 1장) + flyway_schema_history 1 = 25
 */
@IntegrationTest
class SchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("앱이 켜지면 Flyway가 02-erd.md의 테이블 24개와 이력 표(flyway_schema_history)를 만든다")
    void should_create25Tables_when_appStarts() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Integer.class);

        assertThat(count).as("02-erd.md 1장의 테이블 24개 + flyway_schema_history").isEqualTo(25);
    }

    @Test
    @DisplayName("빈 DB에서 V1·V2·V3가 순서대로 성공적으로 적용되고 이력에 남는다")
    void should_recordV1AndV2_when_emptyDb() {
        var history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).extracting(r -> r.get("version")).containsExactly("1", "2", "3");
        assertThat(history).extracting(r -> r.get("success")).containsOnly(true);
    }
}
