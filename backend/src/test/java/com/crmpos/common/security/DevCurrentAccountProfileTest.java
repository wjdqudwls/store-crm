package com.crmpos.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crmpos.common.repository.StaffRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 임시 계정 구현은 prod가 아닐 때만 켜진다 (B0-3에서 JWT 구현으로 바뀔 때까지). */
class DevCurrentAccountProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(StaffRepository.class, () -> mock(StaffRepository.class))
            .withUserConfiguration(DevCurrentAccountProvider.class);

    @Test
    @DisplayName("prod 프로필이 아니면 임시 계정 구현이 켜진다")
    void should_enableDevProvider_when_notProd() {
        runner.run(context -> assertThat(context).hasSingleBean(CurrentAccountProvider.class));
    }

    @Test
    @DisplayName("prod 프로필이면 임시 계정 구현이 꺼진다")
    void should_disableDevProvider_when_prod() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(CurrentAccountProvider.class));
    }
}
