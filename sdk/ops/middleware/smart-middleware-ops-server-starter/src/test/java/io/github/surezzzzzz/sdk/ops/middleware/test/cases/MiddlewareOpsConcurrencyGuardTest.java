package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsConcurrencyGuard;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 两级并发预算测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MiddlewareOpsConcurrencyGuardTest {

    @Test
    void shouldReleaseGlobalPermitWhenDatasourceBudgetIsExhausted() throws Exception {
        MiddlewareOpsConcurrencyGuard guard = new MiddlewareOpsConcurrencyGuard(2, 1);
        MiddlewareOpsRequest first = request("redis-a");
        MiddlewareOpsRequest second = request("redis-b");

        AutoCloseable firstPermit = guard.acquire(first);
        try {
            MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class, () -> guard.acquire(first));
            log.info("同数据源并发预算耗尽：status={}，message={}", exception.getStatus(), exception.getMessage());
            assertEquals(429, exception.getStatus().value());
            assertEquals("目标数据源查询并发预算已耗尽", exception.getMessage());

            AutoCloseable secondPermit = guard.acquire(second);
            secondPermit.close();
        } finally {
            firstPermit.close();
        }
    }

    @Test
    void shouldReleasePermitsAfterClose() throws Exception {
        MiddlewareOpsConcurrencyGuard guard = new MiddlewareOpsConcurrencyGuard(1, 1);
        MiddlewareOpsRequest request = request("redis-a");

        try (AutoCloseable ignored = guard.acquire(request)) {
            MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class, () -> guard.acquire(request));
            log.info("全局并发预算耗尽：status={}，message={}", exception.getStatus(), exception.getMessage());
            assertEquals("运维查询并发预算已耗尽", exception.getMessage());
        }
        AutoCloseable reacquiredPermit = guard.acquire(request);
        reacquiredPermit.close();
    }

    private MiddlewareOpsRequest request(final String datasourceKey) {
        return new MiddlewareOpsRequest() {
            @Override
            public MiddlewareOpsCapability getCapability() {
                return MiddlewareOpsCapability.REDIS_SUMMARY;
            }

            @Override
            public String getDatasourceKey() {
                return datasourceKey;
            }

            @Override
            public String getResourceScope() {
                return "datasource-summary";
            }
        };
    }
}
