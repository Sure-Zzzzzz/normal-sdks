package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.configuration.TaskRetryAutoConfiguration;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task Retry 自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
class TaskRetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskRetryAutoConfiguration.class));

    @Test
    @DisplayName("测试零配置时注册默认 Bean")
    void shouldRegisterBeansWhenPropertyMissing() {
        contextRunner.run(context -> {
            log.info("TaskRetryExecutor Bean 数量: {}", context.getBeansOfType(TaskRetryExecutor.class).size());
            assertTrue(context.containsBean("defaultTaskRetryExecutor"), "零配置应注册默认执行器");
            assertTrue(context.containsBean("threadRetrySleeper"), "零配置应注册默认等待器");
            assertTrue(context.containsBean("defaultRetryPredicate"), "零配置应注册默认重试判断器");
            assertTrue(context.containsBean("noopRetryListener"), "零配置应注册默认监听器");
            assertEquals(1, context.getBeansOfType(TaskRetryExecutor.class).size(), "执行器 Bean 数量应正确");
        });
    }

    @Test
    @DisplayName("测试 enable=true 时注册默认 Bean")
    void shouldRegisterBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("io.github.surezzzzzz.sdk.retry.task.enable=true")
                .run(context -> {
                    log.info("TaskRetryExecutor Bean 数量: {}", context.getBeansOfType(TaskRetryExecutor.class).size());
                    assertTrue(context.containsBean("defaultTaskRetryExecutor"), "应注册默认执行器");
                    assertTrue(context.containsBean("threadRetrySleeper"), "应注册默认等待器");
                    assertTrue(context.containsBean("defaultRetryPredicate"), "应注册默认重试判断器");
                    assertTrue(context.containsBean("noopRetryListener"), "应注册默认监听器");
                    assertEquals(1, context.getBeansOfType(TaskRetryExecutor.class).size(), "执行器 Bean 数量应正确");
                });
    }

    @Test
    @DisplayName("测试 enable=false 时不注册默认 Bean")
    void shouldNotRegisterBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("io.github.surezzzzzz.sdk.retry.task.enable=false")
                .run(context -> {
                    assertFalse(context.containsBean("defaultTaskRetryExecutor"), "不应注册默认执行器");
                    assertFalse(context.containsBean("threadRetrySleeper"), "不应注册默认等待器");
                    assertFalse(context.containsBean("defaultRetryPredicate"), "不应注册默认重试判断器");
                    assertFalse(context.containsBean("noopRetryListener"), "不应注册默认监听器");
                    assertEquals(0, context.getBeansOfType(TaskRetryExecutor.class).size(), "执行器 Bean 数量应为 0");
                });
    }

    @Test
    @DisplayName("测试自定义 Bean 覆盖默认实现")
    void shouldUseCustomBeans() {
        contextRunner
                .withBean("customRetrySleeper", RetrySleeper.class, () -> delayMillis -> {
                })
                .withBean("customRetryPredicate", RetryPredicate.class, () -> (exception, attempt, request) -> false)
                .withPropertyValues("io.github.surezzzzzz.sdk.retry.task.enable=true")
                .run(context -> {
                    assertTrue(context.containsBean("customRetrySleeper"), "应注册自定义等待器");
                    assertTrue(context.containsBean("customRetryPredicate"), "应注册自定义判断器");
                    assertFalse(context.containsBean("threadRetrySleeper"), "自定义等待器存在时不应注册默认等待器");
                    assertFalse(context.containsBean("defaultRetryPredicate"), "自定义判断器存在时不应注册默认判断器");
                });
    }
}
