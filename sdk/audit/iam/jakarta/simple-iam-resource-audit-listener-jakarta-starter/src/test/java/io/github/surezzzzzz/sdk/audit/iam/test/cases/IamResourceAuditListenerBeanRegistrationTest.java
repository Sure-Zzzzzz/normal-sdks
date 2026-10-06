package io.github.surezzzzzz.sdk.audit.iam.test.cases;

import io.github.surezzzzzz.sdk.audit.iam.resource.configuration.SimpleIamResourceAuditListenerAutoConfiguration;
import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.handler.impl.LogIamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.listener.IamResourceAuditEventListener;
import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import io.github.surezzzzzz.sdk.audit.iam.resource.provider.IamResourceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
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
 * 宿主通过 @Bean 注册 Handler 的装配与真实异步事件消费回归，禁止直接调用监听器代替验收。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamResourceAuditListenerBeanRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfiguration.class)
            .withConfiguration(AutoConfigurations.of(SimpleIamResourceAuditListenerAutoConfiguration.class));

    @Test
    void shouldRegisterListenerWithDefaultLogHandler() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(LogIamResourceAuditHandler.class)
                .hasSingleBean(IamResourceAuditEventListener.class));
    }

    @Test
    void shouldConsumeEventWithHostBeanHandlerAndOptionalTraceIdAbsent() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(IamResourceAuditEventListener.class)
                    .hasSingleBean(LogIamResourceAuditHandler.class).hasSingleBean(RecordingHandler.class);
            assertThat(context.getBeansOfType(IamResourceAuditHandler.class)).hasSize(2);
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            Thread publisherThread = Thread.currentThread();
            ResourceAccessEvent event = event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);

            context.publishEvent(event);

            IamResourceAuditRecord record = handler.records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.getAuthenticationSourceId()).isEqualTo(event.getAuthenticationSourceId());
            assertThat(record.getSubjectType()).isEqualTo("HUMAN");
            assertThat(record.getSubjectId()).isEqualTo("audit-user");
            assertThat(record.getApplicationCode()).isEqualTo("audit-app");
            assertThat(record.getRequestId()).isEqualTo("audit-request");
            assertThat(record.getRequestUri()).isEqualTo("/api/example");
            assertThat(record.getHttpMethod()).isEqualTo("GET");
            assertThat(record.getRemoteAddr()).isEqualTo("192.0.2.1");
            assertThat(record.getUserAgent()).isEqualTo("audit-test-agent");
            assertThat(record.getTimestamp()).isEqualTo(event.getTimestamp());
            assertThat(record.getTraceId()).isNull();
            assertThat(handler.consumerThread.get()).isNotSameAs(publisherThread);
            assertThat(handler.records).isEmpty();
        });
    }

    @Test
    void shouldConsumeEventWhenExplicitlyEnabledAndPreserveHostTraceId() {
        runner.withPropertyValues("io.github.surezzzzzz.sdk.audit.iam.resource.enable=true")
                .withUserConfiguration(HandlerConfiguration.class, TraceConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(IamResourceAuditEventListener.class);
                    context.publishEvent(event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID));
                    IamResourceAuditRecord record = context.getBean(RecordingHandler.class).records
                            .poll(5, TimeUnit.SECONDS);
                    assertThat(record).isNotNull();
                    assertThat(record.getTraceId()).isEqualTo("trace-bean");
                });
    }

    @Test
    void shouldKeepHostHandlerButNotRegisterOfficialComponentsWhenDisabled() {
        runner.withPropertyValues("io.github.surezzzzzz.sdk.audit.iam.resource.enable=false")
                .withUserConfiguration(HandlerConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RecordingHandler.class)
                            .doesNotHaveBean(IamResourceAuditEventListener.class)
                            .doesNotHaveBean(LogIamResourceAuditHandler.class);
                    context.publishEvent(event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID));
                    assertThat(context.getBean(RecordingHandler.class).records).isEmpty();
                });
    }

    @Test
    void shouldIgnoreOtherSourceBeforeConsumingIamEvent() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(IamResourceAuditEventListener.class);
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            context.publishEvent(event("aksk"));
            context.publishEvent(event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID));

            // 单线程执行器按序消费；收到 IAM 事件证明此前的其他来源事件已经处理完毕。
            IamResourceAuditRecord record = handler.records.poll(5, TimeUnit.SECONDS);
            assertThat(record).isNotNull();
            assertThat(record.getAuthenticationSourceId())
                    .isEqualTo(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);
            assertThat(handler.records).isEmpty();
        });
    }

    @Test
    void shouldContinueDispatchAfterAnotherHostHandlerFails() {
        runner.withUserConfiguration(HandlerConfiguration.class, FailingHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(IamResourceAuditEventListener.class);
                    assertThat(context.getBeansOfType(IamResourceAuditHandler.class)).hasSize(3);
                    context.publishEvent(event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID));
                    assertThat(context.getBean(RecordingHandler.class).records.poll(5, TimeUnit.SECONDS))
                            .isNotNull();
                    assertThat(context.getBean(FailingHandler.class).invocations.get()).isEqualTo(1);
                });
    }

    @Test
    void shouldConsumeEventWithNullTraceIdWhenProviderFails() {
        runner.withUserConfiguration(HandlerConfiguration.class, FailingTraceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(IamResourceAuditEventListener.class);
                    context.publishEvent(event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID));
                    IamResourceAuditRecord record = context.getBean(RecordingHandler.class).records
                            .poll(5, TimeUnit.SECONDS);
                    assertThat(record).isNotNull();
                    assertThat(record.getTraceId()).isNull();
                });
    }

    @Test
    void shouldBackOffToHostListenerWithoutDuplicateEventDelivery() {
        runner.withUserConfiguration(HandlerConfiguration.class, ListenerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(IamResourceAuditEventListener.class);
            assertThat(context.getBeanNamesForType(IamResourceAuditEventListener.class))
                    .containsExactly("hostListener");
            context.publishEvent(event(SimpleIamCoreConstant.RESOURCE_AUTHENTICATION_SOURCE_ID));
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            assertThat(handler.records.poll(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(handler.records).isEmpty();
        });
    }

    private ResourceAccessEvent event(String sourceId) {
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId(sourceId), ResourceSubjectType.HUMAN, "audit-user");
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN, "audit-user", "audit-app", true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null,
                1L, "audit-manifest", "audit-digest", now.minusSeconds(1), now.plusSeconds(60));
        return new ResourceAccessEvent(new VerifiedResourceContext(principal, authorization, "audit-request"),
                "/api/example", "GET", "192.0.2.1", "audit-test-agent");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AsyncConfiguration {
        @Bean
        ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("iam-audit-regression-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerConfiguration {
        @Bean
        RecordingHandler businessHandler() {
            return new RecordingHandler();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TraceConfiguration {
        @Bean
        IamResourceAuditTraceIdProvider traceIdProvider() {
            return () -> "trace-bean";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingTraceConfiguration {
        @Bean
        IamResourceAuditTraceIdProvider traceIdProvider() {
            return () -> {
                throw new IllegalStateException("trace provider failure");
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
    static class ListenerConfiguration {
        @Bean
        IamResourceAuditEventListener hostListener(RecordingHandler handler) {
            return new IamResourceAuditEventListener(Collections.singletonList(handler), null);
        }
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class FailingHandler implements IamResourceAuditHandler {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void handle(IamResourceAuditRecord record) {
            invocations.incrementAndGet();
            throw new IllegalStateException("handler failure");
        }
    }

    static class RecordingHandler implements IamResourceAuditHandler {
        private final BlockingQueue<IamResourceAuditRecord> records = new LinkedBlockingQueue<>();
        private final AtomicReference<Thread> consumerThread = new AtomicReference<>();

        @Override
        public void handle(IamResourceAuditRecord record) {
            consumerThread.set(Thread.currentThread());
            records.add(record);
        }
    }
}
