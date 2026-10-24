package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.scheduler.IamTrustedApplicationCleanupScheduler;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationCleanupWorker;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 可信应用删除调度器的停机边界测试。
 *
 * @author surezzzzzz
 */
class IamTrustedApplicationCleanupSchedulerTest {

    @Test
    void testStopSchedulingPreventsLockAcquisition() {
        IamTrustedApplicationCleanupWorker cleanupWorker = mock(IamTrustedApplicationCleanupWorker.class);
        SimpleRedisLock simpleRedisLock = mock(SimpleRedisLock.class);
        IamTrustedApplicationCleanupScheduler scheduler =
                new IamTrustedApplicationCleanupScheduler(cleanupWorker, simpleRedisLock);

        scheduler.onApplicationEvent(new ContextClosedEvent(mock(ApplicationContext.class)));
        scheduler.processNextOperation();

        verifyNoInteractions(cleanupWorker, simpleRedisLock);
    }
}
