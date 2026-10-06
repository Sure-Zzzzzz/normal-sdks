package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.resource.configuration.AkskResourceAuditListenerAutoConfiguration;
import io.github.surezzzzzz.sdk.audit.aksk.resource.configuration.AkskResourceAuditListenerProperties;
import io.github.surezzzzzz.sdk.audit.aksk.resource.constant.AkskResourceAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.impl.LogAkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.listener.AkskAuditEventListener;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.resource.provider.AkskAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport.AsyncConfiguration;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport.HandlerConfiguration;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport.RecordingHandler;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 宿主声明 Handler 的真实装配、异步事件消费与边界回归。
 *
 * @author surezzzzzz
 */
@Slf4j
class AkskAuditListenerRegistrationTest {
    private static final String PREFIX = AkskResourceAuditListenerConstant.CONFIG_PREFIX;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfiguration.class)
            .withConfiguration(AutoConfigurations.of(AkskResourceAuditListenerAutoConfiguration.class));

    @Test
    void shouldNotRegisterListenerWithoutHandlerByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AkskResourceAuditListenerProperties.class)
                    .doesNotHaveBean(AkskAuditHandler.class).doesNotHaveBean(AkskAuditEventListener.class);
            AkskResourceAuditListenerProperties properties = context.getBean(AkskResourceAuditListenerProperties.class);
            assertThat(properties.isEnable()).isTrue();
            assertThat(properties.getHandler().getLog().isEnabled()).isFalse();
        });
    }

    @Test
    void shouldRegisterListenerWithLogHandlerOnlyWhenEnabled() {
        runner.withPropertyValues(PREFIX + ".handler.log.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(LogAkskAuditHandler.class)
                    .hasSingleBean(AkskAuditEventListener.class);
            assertThat(context.getBean(AkskResourceAuditListenerProperties.class).getHandler().getLog().isEnabled())
                    .isTrue();
        });
    }

    @Test
    void shouldRegisterListenerWithHostBeanAndPreserveAllEventFields() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AkskAuditEventListener.class)
                    .hasSingleBean(RecordingHandler.class).doesNotHaveBean(LogAkskAuditHandler.class);
            Thread publisherThread = Thread.currentThread();
            ResourceAccessEvent event = AkskAuditTestSupport.event(AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID,
                    "request-bean");
            context.publishEvent(event);
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            assertThat(handler.records).hasSize(1);
            AkskAuditRecord record = handler.records.remove();
            assertThat(record.getAuthenticationSourceId()).isEqualTo(event.getAuthenticationSourceId());
            assertThat(record.getSubjectType()).isEqualTo(event.getSubjectType().name());
            assertThat(record.getSubjectId()).isEqualTo(event.getSubjectId());
            assertThat(record.getApplicationCode()).isEqualTo(event.getApplicationCode());
            assertThat(record.getRequestId()).isEqualTo(event.getRequestId());
            assertThat(record.getRequestUri()).isEqualTo(event.getRequestUri());
            assertThat(record.getHttpMethod()).isEqualTo(event.getHttpMethod());
            assertThat(record.getRemoteAddr()).isEqualTo(event.getRemoteAddr());
            assertThat(record.getUserAgent()).isEqualTo(event.getUserAgent());
            assertThat(record.getTimestamp()).isEqualTo(event.getTimestamp());
            assertThat(record.getTraceId()).isNull();
            assertThat(handler.consumerThread.get()).isNotSameAs(publisherThread);
        });
    }

    @Test
    void shouldConsumeWithScannedComponentHandler() {
        runner.withUserConfiguration(ComponentConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ComponentHandler.class)
                    .hasSingleBean(AkskAuditEventListener.class);
            context.publishEvent(AkskAuditTestSupport.event("aksk", "request-component"));
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            assertThat(context.getBean(ComponentHandler.class).records).hasSize(1);
        });
    }

    @Test
    void shouldAllowLogAndBusinessHandlersTogether() {
        runner.withPropertyValues(PREFIX + ".handler.log.enabled=true")
                .withUserConfiguration(HandlerConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AkskAuditEventListener.class)
                            .hasSingleBean(LogAkskAuditHandler.class);
                    assertThat(context.getBeansOfType(AkskAuditHandler.class)).hasSize(2);
                    context.publishEvent(AkskAuditTestSupport.event("aksk", "request-log-business"));
                    AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
                    assertThat(context.getBean(RecordingHandler.class).records).hasSize(1);
                });
    }

    @Test
    void shouldDisableOfficialComponentsButKeepHostHandler() {
        runner.withPropertyValues(PREFIX + ".enable=false", PREFIX + ".handler.log.enabled=true")
                .withUserConfiguration(HandlerConfiguration.class).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RecordingHandler.class)
                            .doesNotHaveBean(AkskAuditEventListener.class).doesNotHaveBean(LogAkskAuditHandler.class);
                    context.publishEvent(AkskAuditTestSupport.event("aksk", "request-disabled"));
                    AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
                    assertThat(context.getBean(RecordingHandler.class).records).isEmpty();
                });
    }

    @Test
    void shouldConsumeWhenExplicitlyEnabled() {
        runner.withPropertyValues(PREFIX + ".enable=true").withUserConfiguration(HandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AkskAuditEventListener.class);
                    context.publishEvent(AkskAuditTestSupport.event("aksk", "request-enabled"));
                    AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
                    assertThat(context.getBean(RecordingHandler.class).records).hasSize(1);
                });
    }

    @Test
    void shouldFilterIamAndUnknownSourcesBeforeAkskConsumption() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            context.publishEvent(AkskAuditTestSupport.event("iam", "request-iam"));
            context.publishEvent(AkskAuditTestSupport.event("other", "request-other"));
            context.publishEvent(AkskAuditTestSupport.event("aksk", "request-allowed"));
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            assertThat(handler.records).hasSize(1);
            assertThat(handler.records.remove().getRequestId()).isEqualTo("request-allowed");
        });
    }

    @Test
    void shouldPreserveTraceIdFromOptionalProvider() {
        runner.withUserConfiguration(HandlerConfiguration.class, TraceConfiguration.class).run(context -> {
            context.publishEvent(AkskAuditTestSupport.event("aksk", "request-trace"));
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            assertThat(context.getBean(RecordingHandler.class).records.remove().getTraceId()).isEqualTo("trace-test");
        });
    }

    @Test
    void shouldKeepConsumingWhenTraceProviderThrows() {
        runner.withUserConfiguration(HandlerConfiguration.class, FailingTraceConfiguration.class).run(context -> {
            context.publishEvent(AkskAuditTestSupport.event("aksk", "request-failing-trace"));
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            assertThat(context.getBean(RecordingHandler.class).records).hasSize(1);
            assertThat(context.getBean(RecordingHandler.class).records.remove().getTraceId()).isNull();
        });
    }

    @Test
    void shouldIsolateHandlerExceptionsAndRecordMutations() {
        runner.withUserConfiguration(HandlerConfiguration.class, MutatingConfiguration.class).run(context -> {
            context.publishEvent(AkskAuditTestSupport.event("aksk", "request-original"));
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            assertThat(context.getBean(MutatingHandler.class).calls.get()).isEqualTo(1);
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            assertThat(handler.records).hasSize(1);
            assertThat(handler.records.remove().getRequestId()).isEqualTo("request-original");
        });
    }

    @Test
    void shouldBackOffByListenerTypeWithoutDuplicateDispatch() {
        runner.withUserConfiguration(HandlerConfiguration.class, ListenerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AkskAuditEventListener.class);
            assertThat(context.getBeanNamesForType(AkskAuditEventListener.class)).containsExactly("hostListener");
            context.publishEvent(AkskAuditTestSupport.event("aksk", "request-host-listener"));
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            assertThat(context.getBean(RecordingHandler.class).records).hasSize(1);
        });
    }

    @Test
    void shouldPreserveEachEventInQueuedBurst() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            for (int index = 0; index < 20; index++) {
                context.publishEvent(AkskAuditTestSupport.event("aksk", "request-" + index));
            }
            AkskAuditTestSupport.awaitDispatch(context.getBean(ThreadPoolTaskExecutor.class));
            RecordingHandler handler = context.getBean(RecordingHandler.class);
            assertThat(handler.records).hasSize(20);
            HashSet<String> identifiers = new HashSet<>();
            handler.records.forEach(record -> identifiers.add(record.getRequestId()));
            assertThat(identifiers).hasSize(20);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ComponentHandler.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ComponentHandler.class))
    static class ComponentConfiguration {
    }

    @Component
    static class ComponentHandler extends RecordingHandler {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TraceConfiguration {
        @Bean
        AkskAuditTraceIdProvider traceIdProvider() {
            return () -> "trace-test";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingTraceConfiguration {
        @Bean
        AkskAuditTraceIdProvider traceIdProvider() {
            return () -> {
                throw new IllegalStateException("test provider failure");
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutatingConfiguration {
        @Bean
        MutatingHandler mutatingHandler() {
            return new MutatingHandler();
        }
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class MutatingHandler implements AkskAuditHandler {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void handle(AkskAuditRecord record) {
            calls.incrementAndGet();
            record.setRequestId("mutated");
            throw new IllegalStateException("test handler failure");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfiguration {
        @Bean
        AkskAuditEventListener hostListener(RecordingHandler handler) {
            return new AkskAuditEventListener(Collections.singletonList(handler), null);
        }
    }
}
