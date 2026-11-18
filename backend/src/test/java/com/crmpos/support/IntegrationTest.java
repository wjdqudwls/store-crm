package com.crmpos.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 앱 전체를 띄우고 Testcontainers MariaDB에 붙는 통합 테스트용 표시 (DB 테스트는 모두 이것을 쓴다). */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestMariaDb.class)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
