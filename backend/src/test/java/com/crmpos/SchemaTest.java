package com.crmpos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * schema.sql이 02-erd.md의 테이블을 모두 만드는지 확인한다 (B0-5).
 * 테이블 수: common 3 + product 2 + sales 7 + customer 12 = 24 (02-erd.md 1장)
 */
@SpringBootTest
class SchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("앱이 켜지면 schema.sql이 02-erd.md의 테이블 24개를 모두 만든다")
    void should_create24Tables_when_appStarts() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Integer.class);

        assertThat(count).as("02-erd.md 1장의 테이블 수").isEqualTo(24);
    }
}
