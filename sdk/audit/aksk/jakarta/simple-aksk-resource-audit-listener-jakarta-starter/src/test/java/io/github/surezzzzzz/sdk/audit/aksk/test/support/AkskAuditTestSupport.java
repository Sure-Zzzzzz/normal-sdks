package io.github.surezzzzzz.sdk.audit.aksk.test.support;

import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.event.ResourceAccessEvent;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 公共测试事件和单线程异步消费屏障，不替代正式资源认证链。
 *
 * @author surezzzzzz
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AkskAuditTestSupport {
    /**
     * 创建不含认证凭据的公共资源事件。
     */
    public static ResourceAccessEvent event(String sourceId, String requestId) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE, "service-client", "resource-app", true,
                Collections.emptyList(), Collections.emptyList(), Collections.singletonList("resource.read"),
                null, 1L, "audit-manifest", "audit-digest", now.minusSeconds(1), now.plusSeconds(60));
        VerifiedResourceContext context = new VerifiedResourceContext(new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId(sourceId), ResourceSubjectType.SERVICE, "service-client"),
                authorization, requestId);
        return new ResourceAccessEvent(context, "/api/resource", "GET", "192.0.2.1", "audit-test-agent");
    }

    /**
     * 等待此前已入队的所有消费完成，负向断言不依赖固定等待。
     */
    public static void awaitDispatch(ThreadPoolTaskExecutor executor) throws Exception {
        executor.submit(() -> {
        }).get(5, TimeUnit.SECONDS);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class AsyncConfiguration {
        @Bean
        ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("aksk-audit-test-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class HandlerConfiguration {
        @Bean
        RecordingHandler businessHandler() {
            return new RecordingHandler();
        }
    }

    /**
     * 记录真实异步消费结果及线程，用于行为断言。
     */
    public static class RecordingHandler implements AkskAuditHandler {
        public final BlockingQueue<AkskAuditRecord> records = new LinkedBlockingQueue<>();
        public final AtomicReference<Thread> consumerThread = new AtomicReference<>();

        @Override
        public void handle(AkskAuditRecord record) {
            consumerThread.set(Thread.currentThread());
            records.add(record);
        }
    }
}
