package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.configuration.SimpleElasticsearchAuditListenerConfiguration;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.configuration.SimpleElasticsearchAuditListenerProperties;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener.EsAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动配置、宿主让位、线程池拒绝与容器生命周期契约。
 */
@Slf4j
class AuditConfigurationTest {
    private final String prefix = SimpleElasticsearchAuditListenerConstant.CONFIG_PREFIX;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleElasticsearchAuditListenerConfiguration.class));

    @Test
    void disabledByDefaultAndExplicitFalse() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(EsAuditEventListener.class);
            assertFalse(context.containsBean(SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME));
        });
        enabled().withPropertyValues(prefix + ".enable=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(EsAuditEventListener.class);
            assertFalse(context.containsBean(SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME));
        });
    }

    @Test
    void enabledWithoutHandlerDoesNotRegisterListener() {
        enabled().run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EsAuditEventListener.class));
    }

    @Test
    void defaultLogHandlerRegistersListenerAndManagedExecutor() {
        enabled().withPropertyValues(prefix + ".handler.log.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(EsAuditEventListener.class).hasSingleBean(EsAuditHandler.class);
            ThreadPoolTaskExecutor pool = context.getBean(ThreadPoolTaskExecutor.class);
            assertEquals(4, pool.getCorePoolSize());
            assertEquals(20, pool.getMaxPoolSize());
            assertEquals(2000, pool.getQueueCapacity());
            assertEquals(SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_THREAD_NAME_PREFIX, pool.getThreadNamePrefix());
            assertFalse(pool.getThreadPoolExecutor().isShutdown());
        });
    }

    @Test
    void customHandlerRegistersListenerAndNamedExecutorWins() {
        Executor custom = Runnable::run;
        enabled().withBean(EsAuditHandler.class, () -> record -> {
                })
                .withBean(SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME, Executor.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EsAuditEventListener.class);
                    assertSame(custom, context.getBean(SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME));
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutor.class);
                });
    }

    @Test
    void contextCloseShutsDownOwnedExecutor() {
        ThreadPoolTaskExecutor[] captured = new ThreadPoolTaskExecutor[1];
        enabled().run(context -> captured[0] = context.getBean(ThreadPoolTaskExecutor.class));
        assertTrue(captured[0].getThreadPoolExecutor().isShutdown());
    }

    @Test
    void bindsAllExecutorSettings() {
        enabled().withPropertyValues(prefix + ".executor.core-size=1", prefix + ".executor.max-size=2",
                prefix + ".executor.queue-capacity=3", prefix + ".executor.keep-alive-seconds=4",
                prefix + ".executor.reject-policy=abort").run(context -> {
            assertThat(context).hasNotFailed();
            SimpleElasticsearchAuditListenerProperties.Executor values = context.getBean(SimpleElasticsearchAuditListenerProperties.class).getExecutor();
            assertEquals(1, values.getCoreSize());
            assertEquals(2, values.getMaxSize());
            assertEquals(3, values.getQueueCapacity());
            assertEquals(4, values.getKeepAliveSeconds());
            assertEquals("abort", values.getRejectPolicy());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"core-size=0", "core-size=-1", "max-size=0", "max-size=3",
            "queue-capacity=-1", "keep-alive-seconds=-1", "reject-policy=unknown", "reject-policy="})
    void rejectsInvalidExecutorSettings(String value) {
        enabled().withPropertyValues(prefix + ".executor." + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CALLER_RUNS", "DISCARD", "DISCARD_OLDEST", "ABORT"})
    void saturationHasDefinedPolicyAndDoesNotLeakWorker(String policy) throws Exception {
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean();
        enabled().withPropertyValues(prefix + ".executor.core-size=1", prefix + ".executor.max-size=1",
                        prefix + ".executor.queue-capacity=0", prefix + ".executor.reject-policy=" + policy)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThreadPoolTaskExecutor pool = context.getBean(ThreadPoolTaskExecutor.class);
                    pool.execute(() -> {
                        occupied.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    try {
                        assertTrue(occupied.await(5, TimeUnit.SECONDS));
                        if ("ABORT".equals(policy))
                            assertThrows(RuntimeException.class, () -> pool.execute(() -> ran.set(true)));
                        else assertDoesNotThrow(() -> pool.execute(() -> ran.set(true)));
                        assertEquals("CALLER_RUNS".equals(policy), ran.get());
                    } finally {
                        release.countDown();
                    }
                });
    }

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues(prefix + ".enable=true");
    }
}
