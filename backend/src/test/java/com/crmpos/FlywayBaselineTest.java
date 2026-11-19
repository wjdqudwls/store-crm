package com.crmpos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * 이미 표가 있는 DB(Flyway 이전에 만든 로컬 데모 DB)를 baseline으로 이어 가는지 확인한다 (ADR-039).
 * 앱 전체가 쓰는 DB와 따로, 이 테스트만 쓰는 MariaDB를 하나 더 띄운다.
 */
class FlywayBaselineTest {

    private Flyway flyway(MariaDBContainer db) {
        return Flyway.configure()
                .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("1").load();
    }

    @Test
    @DisplayName("이미 표가 있는 DB는 V1을 건너뛰고(baseline) V2만 적용하며, 다시 실행해도 같다")
    void should_baselineV1AndApplyV2_when_tablesAlreadyExist() throws SQLException {
        try (MariaDBContainer db = new MariaDBContainer("mariadb:11")) {
            db.start();
            try (Connection c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                // Flyway 이전 방식으로 표만 만들어 둔 DB를 흉내 낸다
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V1__baseline.sql"));

                flyway(db).migrate();
                int firstRun = flyway(db).migrate().migrationsExecuted;

                try (Statement st = c.createStatement()) {
                    ResultSet rs = st.executeQuery("SELECT version, type FROM flyway_schema_history ORDER BY installed_rank");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("version")).isEqualTo("1");
                    assertThat(rs.getString("type")).isEqualTo("BASELINE");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("version")).isEqualTo("2");
                    rs = st.executeQuery("SELECT COUNT(*) FROM store");
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
                assertThat(firstRun).as("두 번째 실행은 할 일이 없다").isZero();
            }
        }
    }
}
