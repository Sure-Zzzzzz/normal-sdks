package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.cache.warmup.SmartCacheWarmUpProcessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动预热线程池饱和（AbortPolicy 拒绝）行为测试
 *
 * <p>验证当 {@code smartCacheWarmUpExecutor} 拒绝提交时，预热处理器以
 * {@link IllegalStateException} 明确失败当前预热批次，而不是静默丢任务或 CallerRuns 阻塞调用线程。
 *
 * <p>测试由统一 Spring Boot 测试启动类加载；用例直接构造 {@link SmartCacheWarmUpProcessor} 并注入拒绝执行器，
 * 以 mock {@link ApplicationContext} 提供一个带 {@code @SmartCacheWarmUp} 的 Bean，
 * 触发 {@code ContextRefreshedEvent}，避免依赖线程调度或长 sleep。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class CacheWarmUpSaturationTest {

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("无法注入字段 " + fieldName + "：" + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("预热线程池拒绝提交时，onApplicationEvent 抛出 IllegalStateException 而非静默丢弃")
    void shouldFailWarmupBatchWhenExecutorRejects() {
        // 始终拒绝的执行器，模拟 AbortPolicy 队列饱和
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("测试预热线程池饱和");
        };

        // 带 @SmartCacheWarmUp 的 Bean，返回 Map 以通过返回类型校验
        Object warmupBean = new Object() {
            @SmartCacheWarmUp(cacheName = "saturation-warmup", order = 1)
            public Map<String, Object> warmUp() {
                Map<String, Object> data = new HashMap<>();
                data.put("k", "v");
                return data;
            }
        };

        ApplicationContext mockContext = mock(ApplicationContext.class);
        when(mockContext.getParent()).thenReturn(null);
        when(mockContext.getBeanDefinitionNames()).thenReturn(new String[]{"warmupBean"});
        when(mockContext.getBean("warmupBean")).thenReturn(warmupBean);

        SmartCacheWarmUpProcessor processor = new SmartCacheWarmUpProcessor();
        injectField(processor, "warmupExecutor", rejectingExecutor);

        ContextRefreshedEvent event = new ContextRefreshedEvent(mockContext);
        log.info("预热线程池饱和测试输入：预热 Bean 数量：{}，预期异常类型：{}", 1,
                IllegalStateException.class.getSimpleName());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> processor.onApplicationEvent(event),
                "预热执行器饱和时应抛出 IllegalStateException，而非静默丢任务");

        log.info("预热线程池饱和测试输出：消息：{}，根因：{}", ex.getMessage(),
                ex.getCause().getClass().getSimpleName());
        assertTrue(ex.getMessage().contains("饱和"),
                "异常消息应说明线程池饱和，实际：" + ex.getMessage());
        assertTrue(ex.getCause() instanceof RejectedExecutionException,
                "根因应为 RejectedExecutionException，实际：" + ex.getCause());
    }
}
