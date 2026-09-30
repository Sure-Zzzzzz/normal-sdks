package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.configuration.TaskRetryAutoConfiguration;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.listener.RetryListener;
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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
    @DisplayName("测试自定义 TaskRetryExecutor 只覆盖默认执行器")
    void shouldBackOffDefaultExecutorWhenCustomExecutorExists() {
        TaskRetryExecutor customExecutor = mock(TaskRetryExecutor.class);

        contextRunner
                .withBean("customTaskRetryExecutor", TaskRetryExecutor.class, () -> customExecutor)
                .run(context -> {
                    assertSame(customExecutor, context.getBean(TaskRetryExecutor.class), "应保留调用方执行器");
                    assertFalse(context.containsBean("defaultTaskRetryExecutor"), "自定义执行器存在时不应注册默认执行器");
                    assertTrue(context.containsBean("threadRetrySleeper"), "不相关默认等待器应保留");
                    assertTrue(context.containsBean("defaultRetryPredicate"), "不相关默认判断器应保留");
                    assertTrue(context.containsBean("noopRetryListener"), "不相关默认监听器应保留");
                });
    }

    @Test
    @DisplayName("测试自定义 RetrySleeper 只覆盖默认等待器")
    void shouldBackOffDefaultSleeperWhenCustomSleeperExists() {
        contextRunner
                .withBean("customRetrySleeper", RetrySleeper.class, () -> delayMillis -> {
                })
                .run(context -> {
                    assertTrue(context.containsBean("customRetrySleeper"), "应注册自定义等待器");
                    assertFalse(context.containsBean("threadRetrySleeper"), "自定义等待器存在时不应注册默认等待器");
                    assertTrue(context.containsBean("defaultTaskRetryExecutor"), "不相关默认执行器应保留");
                    assertTrue(context.containsBean("defaultRetryPredicate"), "不相关默认判断器应保留");
                    assertTrue(context.containsBean("noopRetryListener"), "不相关默认监听器应保留");
                });
    }

    @Test
    @DisplayName("测试自定义 RetryPredicate 只覆盖默认判断器")
    void shouldBackOffDefaultPredicateWhenCustomPredicateExists() {
        contextRunner
                .withBean("customRetryPredicate", RetryPredicate.class, () -> (exception, attempt, request) -> false)
                .run(context -> {
                    assertTrue(context.containsBean("customRetryPredicate"), "应注册自定义判断器");
                    assertFalse(context.containsBean("defaultRetryPredicate"), "自定义判断器存在时不应注册默认判断器");
                    assertTrue(context.containsBean("defaultTaskRetryExecutor"), "不相关默认执行器应保留");
                    assertTrue(context.containsBean("threadRetrySleeper"), "不相关默认等待器应保留");
                    assertTrue(context.containsBean("noopRetryListener"), "不相关默认监听器应保留");
                });
    }

    @Test
    @DisplayName("测试自定义 RetryListener 只覆盖默认监听器")
    void shouldBackOffDefaultListenerWhenCustomListenerExists() {
        RetryListener customListener = mock(RetryListener.class);

        contextRunner
                .withBean("customRetryListener", RetryListener.class, () -> customListener)
                .run(context -> {
                    assertSame(customListener, context.getBean(RetryListener.class), "应保留调用方监听器");
                    assertFalse(context.containsBean("noopRetryListener"), "自定义监听器存在时不应注册默认监听器");
                    assertTrue(context.containsBean("defaultTaskRetryExecutor"), "不相关默认执行器应保留");
                    assertTrue(context.containsBean("threadRetrySleeper"), "不相关默认等待器应保留");
                    assertTrue(context.containsBean("defaultRetryPredicate"), "不相关默认判断器应保留");
                });
    }

    @Test
    @DisplayName("测试 Jakarta 自动装配只通过 imports 注册")
    void shouldRegisterAutoConfigurationOnlyThroughImports() throws IOException {
        ClassLoader classLoader = TaskRetryAutoConfiguration.class.getClassLoader();
        InputStream importsStream = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertNotNull(importsStream, "自动装配 imports 文件必须存在");
        try (InputStream inputStream = importsStream) {
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(imports.contains(TaskRetryAutoConfiguration.class.getName()), "imports 应注册 Task Retry 自动配置");
        }
        Enumeration<URL> legacyFactories = classLoader.getResources("META-INF/spring.factories");
        while (legacyFactories.hasMoreElements()) {
            URL legacyFactory = legacyFactories.nextElement();
            assertFalse(legacyFactory.toString().contains("task-retry-jakarta-starter"),
                    "Task Retry Jakarta 模块不得携带 legacy spring.factories");
        }
    }
}
