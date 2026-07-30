package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AccountHardDeleteSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(StubServiceConfig.class, AccountHardDeleteScheduler.class);

    // 되돌릴 수 없는 삭제라, 설정을 켜지 않은 환경(로컬·테스트·기본 운영)에서는 빈 자체가
    // 등록되지 않아야 한다. 가드가 빠지면 @SpringBootTest에서도 계정이 지워진다.
    @Test
    void schedulerIsNotRegisteredByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(AccountHardDeleteScheduler.class));
    }

    @Test
    void schedulerIsNotRegisteredWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("account.purge.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(AccountHardDeleteScheduler.class));
    }

    @Test
    void schedulerIsRegisteredWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "account.purge.enabled=true",
                        "account.purge.cron=0 */5 * * * *",
                        "account.purge.zone=Asia/Seoul")
                .run(context -> assertThat(context).hasSingleBean(AccountHardDeleteScheduler.class));
    }

    // 예외가 스케줄러 밖으로 나가면 이후 주기가 아예 실행되지 않는다.
    @Test
    void batchFailureDoesNotEscapeTheScheduledMethod() {
        AccountHardDeleteService service = mock(AccountHardDeleteService.class);
        doThrow(new RuntimeException("boom")).when(service).purgeBatch();

        new AccountHardDeleteScheduler(service).purgeWithdrawnAccounts();

        verify(service).purgeBatch();
    }

    @Configuration(proxyBeanMethods = false)
    static class StubServiceConfig {

        @Bean
        AccountHardDeleteService accountHardDeleteService() {
            return mock(AccountHardDeleteService.class);
        }
    }
}
