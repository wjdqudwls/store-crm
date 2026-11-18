package com.crmpos.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * DB가 필요한 모든 테스트가 같이 쓰는 MariaDB 11 컨테이너 (ADR-017). 테스트 전체에서 1개만 띄운다.
 * Docker가 켜져 있어야 한다. H2는 쓰지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMariaDb {

    private static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11");

    static {
        MARIADB.start();
    }

    @Bean
    @ServiceConnection
    MariaDBContainer mariaDb() {
        return MARIADB;
    }
}
