package io.github.surezzzzzz.sdk.audit.license.test.cases;

import io.github.surezzzzzz.sdk.audit.license.configuration.LicenseAuditListenerAutoConfiguration;
import io.github.surezzzzzz.sdk.audit.license.constant.SmartLicenseAuditConstant;
import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.handler.impl.LogLicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.listener.LicenseAuditEventListener;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import io.github.surezzzzzz.sdk.audit.license.provider.LicenseAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents;
import io.github.surezzzzzz.sdk.license.server.event.RoleProjectionChanged;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 宿主 @Bean 处理器的真实装配及异步发布回归，不以直接调用监听器代替官方事件链。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseAuditListenerBeanRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfiguration.class)
            .withConfiguration(AutoConfigurations.of(LicenseAuditListenerAutoConfiguration.class));

    @Test
    void hostBeanHandlerConsumesRoleProjectionEventWithoutInventingAPerson() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LicenseAuditEventListener.class)
                    .hasSingleBean(RecordingHandler.class).doesNotHaveBean(LogLicenseAuditHandler.class);
            assertThat(context.getBeansOfType(LicenseAuditHandler.class)).hasSize(1);
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            Thread publisherThread = Thread.currentThread();
            RoleProjectionChanged event = event();

            context.publishEvent(event);

            LicenseAuditRecord record = handler.records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.getEventType()).isEqualTo(SmartLicenseAuditConstant.EVENT_TYPE_ROLE_PROJECTION_CHANGED);
            assertThat(record.getEventId()).isEqualTo(event.getEventId());
            assertThat(record.getEventVersion()).isEqualTo(event.getEventVersion());
            assertThat(record.getTimestamp()).isEqualTo(event.getOccurredAt().toEpochMilli());
            assertThat(record.getProjectionId()).isEqualTo("projection-example");
            assertThat(record.getCommandId()).isEqualTo("command-example");
            assertThat(record.getDesiredRevision()).isEqualTo(7L);
            assertThat(record.getExecutionMode()).isEqualTo("AUTO");
            assertThat(record.getToState()).isEqualTo("UNCERTAIN");
            assertThat(record.getReasonCategory()).isEqualTo("EXECUTION_IN_PROGRESS");
            assertThat(record.getPrincipalId()).isNull();
            assertThat(record.getRequestId()).isNull();
            assertThat(record.getTraceId()).isNull();
            assertThat(handler.consumerThread.get()).isNotSameAs(publisherThread);
            assertThat(handler.records).isEmpty();
        });
    }

    @Test
    void hostBeanHandlerStillConsumesTheExistingGrantEvent() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LicenseAuditEventListener.class);
            LicenseAuditEvents.GrantCreated event = new LicenseAuditEvents.GrantCreated(
                    "iam:example-person", "request-example", "grant-example", "customer-example",
                    "product-example", "PERSON", "ONLINE", null);
            context.publishEvent(event);

            LicenseAuditRecord record = context.getBean(RecordingHandler.class).records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.getEventType()).isEqualTo(SmartLicenseAuditConstant.EVENT_TYPE_GRANT_CREATED);
            assertThat(record.getEventId()).isEqualTo(event.eventId);
            assertThat(record.getGrantId()).isEqualTo("grant-example");
            assertThat(record.getPrincipalId()).isEqualTo("iam:example-person");
            assertThat(record.getProjectionId()).isNull();
        });
    }

    @Test
    void noHandlerMeansNoOfficialListenerAndDefaultLoggingStaysDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(LicenseAuditEventListener.class)
                    .doesNotHaveBean(LogLicenseAuditHandler.class);
            assertThat(context.getBeansOfType(LicenseAuditHandler.class)).isEmpty();
            context.publishEvent(event());
        });
    }

    @Test
    void explicitlyEnabledLogHandlerRegistersTheListener() {
        runner.withPropertyValues("io.github.surezzzzzz.sdk.audit.license.listener.handler.log.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(LogLicenseAuditHandler.class)
                        .hasSingleBean(LicenseAuditEventListener.class));
    }

    @Test
    void hostTraceIdIsAddedToTheRoleRecord() {
        runner.withUserConfiguration(HandlerConfiguration.class, TraceConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LicenseAuditEventListener.class);
            context.publishEvent(event());
            LicenseAuditRecord record = context.getBean(RecordingHandler.class).records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.getTraceId()).isEqualTo("trace-example");
        });
    }

    @Test
    void traceProviderFailureDoesNotBreakConsumption() {
        runner.withUserConfiguration(HandlerConfiguration.class, FailingTraceConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LicenseAuditEventListener.class);
            context.publishEvent(event());
            LicenseAuditRecord record = context.getBean(RecordingHandler.class).records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.getTraceId()).isNull();
        });
    }

    @Test
    void oneFailingHostHandlerDoesNotPreventOtherHandlers() {
        runner.withUserConfiguration(HandlerConfiguration.class, FailingHandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LicenseAuditEventListener.class);
            assertThat(context.getBeansOfType(LicenseAuditHandler.class)).hasSize(2);
            context.publishEvent(event());
            assertThat(context.getBean(RecordingHandler.class).records.poll(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(context.getBean(FailingHandler.class).invocations.get()).isEqualTo(1);
        });
    }

    @Test
    void existingHostListenerReceivesEachEventOnce() {
        runner.withUserConfiguration(HandlerConfiguration.class, HostListenerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LicenseAuditEventListener.class);
            assertThat(context.getBeanNamesForType(LicenseAuditEventListener.class)).containsExactly("hostListener");
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            context.publishEvent(event());
            assertThat(handler.records.poll(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(handler.records).isEmpty();
        });
    }

    private RoleProjectionChanged event() {
        return new RoleProjectionChanged("event-example", LicenseAuditEvents.EVENT_VERSION, Instant.now(),
                "projection-example", "command-example", 7L, "AUTO", "UNCERTAIN", "EXECUTION_IN_PROGRESS");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncConfiguration {
        @Bean
        ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("license-audit-regression-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerConfiguration {
        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TraceConfiguration {
        @Bean
        LicenseAuditTraceIdProvider traceIdProvider() {
            return () -> "trace-example";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingTraceConfiguration {
        @Bean
        LicenseAuditTraceIdProvider traceIdProvider() {
            return () -> {
                throw new IllegalStateException("测试链路标识失败");
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingHandlerConfiguration {
        @Bean
        FailingHandler failingHandler() {
            return new FailingHandler();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HostListenerConfiguration {
        @Bean
        LicenseAuditEventListener hostListener(RecordingHandler handler) {
            return new LicenseAuditEventListener(Collections.singletonList(handler), null);
        }
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class FailingHandler implements LicenseAuditHandler {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void handle(LicenseAuditRecord record) {
            invocations.incrementAndGet();
            throw new IllegalStateException("测试审计处理器失败");
        }
    }

    static class RecordingHandler implements LicenseAuditHandler {
        private final BlockingQueue<LicenseAuditRecord> records = new LinkedBlockingQueue<>();
        private final AtomicReference<Thread> consumerThread = new AtomicReference<>();

        @Override
        public void handle(LicenseAuditRecord record) {
            consumerThread.set(Thread.currentThread());
            records.add(record);
        }
    }
}
