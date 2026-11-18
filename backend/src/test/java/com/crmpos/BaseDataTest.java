package com.crmpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.crmpos.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/** 기본 데이터 SQL(data.sql)이 앱을 켤 때 들어가고, 다시 실행해도 중복되지 않는지 확인한다 (ADR-019, B0-6). */
@IntegrationTest
class BaseDataTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("앱이 켜지면 기본 매장 2개가 들어 있다")
    void should_haveTwoStores_when_appStarts() {
        List<String> codes = jdbc.queryForList("SELECT code FROM store ORDER BY code", String.class);

        assertThat(codes).containsExactly("GN01", "HD01");
    }

    @Test
    @DisplayName("등급 기준 1번째 버전은 금액 5·티켓 5·등급 컷 5줄이고 경계값이 문서와 같다")
    void should_haveGradePolicyV1_when_appStarts() {
        Integer policies = jdbc.queryForObject("SELECT COUNT(*) FROM grade_policy WHERE version = 1", Integer.class);
        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT kind, COUNT(*) AS n FROM grade_rule GROUP BY kind");
        Integer scoreAt3m = jdbc.queryForObject(
                "SELECT score FROM grade_rule WHERE kind = 'AMOUNT' AND min_value = 3000000", Integer.class);
        String gradeAt70 = jdbc.queryForObject(
                "SELECT grade_code FROM grade_rule WHERE kind = 'GRADE' AND min_value = 70", String.class);

        assertThat(policies).isEqualTo(1);
        assertThat(rules).hasSize(3).allSatisfy(row -> assertThat(((Number) row.get("n")).intValue()).isEqualTo(5));
        assertThat(scoreAt3m).isEqualTo(40);
        assertThat(gradeAt70).isEqualTo("B");
    }

    @Test
    @DisplayName("등급 할인율은 A 5·B 3·C 2·D 1·일반 0이다")
    void should_haveGradeDiscounts_when_appStarts() {
        Map<String, Integer> rates = new java.util.HashMap<>();
        jdbc.query("SELECT grade_code, rate FROM grade_discount",
                rs -> { rates.put(rs.getString("grade_code"), rs.getInt("rate")); });

        assertThat(rates).containsOnly(
                Map.entry("A", 5), Map.entry("B", 3), Map.entry("C", 2), Map.entry("D", 1), Map.entry("NORMAL", 0));
    }

    @Test
    @DisplayName("후속 연락은 2일·2주·2달 3단계이고 2달 단계만 홍보다")
    void should_haveThreeFollowUpRules_when_appStarts() {
        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT name, offset_amount, offset_unit, promotional FROM follow_up_rule ORDER BY sort_order");

        assertThat(rules).extracting(r -> r.get("name"))
                .containsExactly("감사 인사", "착용감 확인", "신상·재방문 안내");
        assertThat(rules).extracting(r -> r.get("offset_unit")).containsExactly("DAY", "WEEK", "MONTH");
        assertThat(rules).extracting(r -> r.get("promotional")).containsExactly(false, false, true);
    }

    @Test
    @DisplayName("기본 데이터 SQL을 다시 실행해도 행이 늘지 않는다")
    void should_notDuplicate_when_dataSqlRunsAgain() {
        int before = countAll();

        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);

        assertThat(countAll()).isEqualTo(before);
    }

    private int countAll() {
        return jdbc.queryForObject(
                "SELECT (SELECT COUNT(*) FROM store) + (SELECT COUNT(*) FROM grade_policy)"
                        + " + (SELECT COUNT(*) FROM grade_rule) + (SELECT COUNT(*) FROM grade_discount)"
                        + " + (SELECT COUNT(*) FROM follow_up_rule)", Integer.class);
    }
}
