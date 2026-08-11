package io.github.surezzzzzz.sdk.ops.middleware.support;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 全局及中间件数据源两级瞬时并发守卫。
 *
 * @author surezzzzzz
 */
public class MiddlewareOpsConcurrencyGuard {

    private final Semaphore globalSemaphore;
    private final int datasourceLimit;
    private final Map<String, Semaphore> datasourceSemaphores = new ConcurrentHashMap<>();

    /**
     * 创建并发守卫。
     *
     * @param globalLimit     全局上限
     * @param datasourceLimit 数据源上限
     */
    public MiddlewareOpsConcurrencyGuard(int globalLimit, int datasourceLimit) {
        this.globalSemaphore = new Semaphore(globalLimit);
        this.datasourceLimit = datasourceLimit;
    }

    /**
     * 尝试获得执行预算。
     *
     * @param request 请求
     * @return 关闭后释放预算的句柄
     */
    public AutoCloseable acquire(MiddlewareOpsRequest request) {
        if (!globalSemaphore.tryAcquire()) {
            throw new MiddlewareOpsException(HttpStatus.TOO_MANY_REQUESTS, "运维查询并发预算已耗尽");
        }
        String key = request.getCapability().getMiddlewareType().getCode() + ":" + request.getDatasourceKey();
        Semaphore datasourceSemaphore = datasourceSemaphores.computeIfAbsent(key,
                ignored -> new Semaphore(datasourceLimit));
        if (!datasourceSemaphore.tryAcquire()) {
            globalSemaphore.release();
            throw new MiddlewareOpsException(HttpStatus.TOO_MANY_REQUESTS, "目标数据源查询并发预算已耗尽");
        }
        return new Permit(globalSemaphore, datasourceSemaphore);
    }

    /**
     * 成功获得的预算句柄。
     */
    private static class Permit implements AutoCloseable {

        private final Semaphore globalSemaphore;
        private final Semaphore datasourceSemaphore;

        private Permit(Semaphore globalSemaphore, Semaphore datasourceSemaphore) {
            this.globalSemaphore = globalSemaphore;
            this.datasourceSemaphore = datasourceSemaphore;
        }

        @Override
        public void close() {
            datasourceSemaphore.release();
            globalSemaphore.release();
        }
    }
}
