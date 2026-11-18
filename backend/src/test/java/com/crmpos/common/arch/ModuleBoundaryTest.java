package com.crmpos.common.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crmpos.archfixture.customer.api.FixtureCustomerApi;
import com.crmpos.archfixture.sales.service.GoodSalesService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 모듈 경계 (ADR-012, ADR-018). 다른 모듈의 controller·service·repository·domain을 import하면 실패한다. */
class ModuleBoundaryTest {

    private static final List<String> MODULES = List.of("sales", "customer", "product");
    private static final List<String> HIDDEN_LAYERS = List.of("controller", "service", "repository", "domain");

    /** 모듈마다 "다른 모듈의 숨긴 층을 쓰지 않는다" 규칙, 마지막에 common 규칙을 만든다. */
    static List<ArchRule> boundaryRules(String root) {
        List<ArchRule> rules = new ArrayList<>();
        for (String module : MODULES) {
            List<String> hidden = new ArrayList<>();
            for (String other : MODULES) {
                if (!other.equals(module)) {
                    for (String layer : HIDDEN_LAYERS) {
                        hidden.add(root + "." + other + "." + layer + "..");
                    }
                }
            }
            rules.add(noClasses().that().resideInAPackage(root + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(hidden.toArray(String[]::new))
                    .as(module + " 모듈은 다른 모듈의 controller·service·repository·domain을 쓰지 않는다")
                    .allowEmptyShould(true));
        }
        // common은 다른 모듈을 부르지 않는다 (판매가 이미 common을 쓰므로 순환이 된다)
        String[] modulePackages = MODULES.stream().map(m -> root + "." + m + "..").toArray(String[]::new);
        rules.add(noClasses().that().resideInAPackage(root + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(modulePackages)
                .as("common은 sales·customer·product를 쓰지 않는다")
                .allowEmptyShould(true));
        return rules;
    }

    @Test
    @DisplayName("실제 코드는 모듈 경계를 지킨다")
    void should_passBoundaryRules_when_realCode() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.crmpos");

        assertThat(classes.size()).isPositive();
        boundaryRules("com.crmpos").forEach(rule -> rule.check(classes));
    }

    @Test
    @DisplayName("다른 모듈의 domain을 import하면 규칙이 실패한다")
    void should_fail_when_importOtherModuleDomain() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.crmpos.archfixture");

        assertThatThrownBy(() -> boundaryRules("com.crmpos.archfixture").get(0).check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadSalesService");
    }

    @Test
    @DisplayName("다른 모듈의 api만 쓰면 규칙을 통과한다")
    void should_pass_when_onlyApiUsed() {
        JavaClasses classes = new ClassFileImporter().importClasses(GoodSalesService.class, FixtureCustomerApi.class);

        boundaryRules("com.crmpos.archfixture").forEach(rule -> rule.check(classes));
    }

    @Test
    @DisplayName("common이 다른 모듈을 import하면 규칙이 실패한다")
    void should_fail_when_commonImportsModule() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.crmpos.archfixture");

        assertThatThrownBy(() -> boundaryRules("com.crmpos.archfixture").get(3).check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BadCommonService");
    }
}
