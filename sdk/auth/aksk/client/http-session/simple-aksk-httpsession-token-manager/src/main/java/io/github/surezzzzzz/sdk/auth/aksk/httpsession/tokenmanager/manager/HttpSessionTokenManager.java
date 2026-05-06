package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor.TokenStatus;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.AbstractTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.annotation.SimpleAkskHttpSessionTokenManagerComponent;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpSession Token Manager
 *
 * <p>基于 HttpSession 的 Token 管理器，继承 {@link AbstractTokenManager} 复用通用流程，
 * 实现 {@link #fetchTokenWithLock} 提供 JVM 本地锁策略。
 *
 * <p>特性：
 * <ul>
 *   <li>无需额外依赖</li>
 *   <li>适用于单实例应用</li>
 *   <li>使用 JVM synchronized 锁防止同一进程内并发获取</li>
 * </ul>
 *
 * <p>多实例部署说明：每个实例维护自己的 Token 缓存，如需共享建议使用 simple-aksk-redis-token-manager。
 *
 * @author surezzzzzz
 */
@SimpleAkskHttpSessionTokenManagerComponent
@Slf4j
public class HttpSessionTokenManager extends AbstractTokenManager {

    private static final Object TOKEN_FETCH_LOCK = new Object();

    public HttpSessionTokenManager(
            SimpleAkskClientCoreProperties coreProperties,
            TokenCacheStrategy tokenCacheStrategy,
            SecurityContextProvider securityContextProvider,
            TaskRetryExecutor retryExecutor
    ) {
        super(coreProperties, tokenCacheStrategy, securityContextProvider, retryExecutor);
    }

    /**
     * 使用 JVM 本地锁获取 Token，防止同一进程内并发获取
     */
    @Override
    protected String fetchTokenWithLock(String cacheKey, String securityContext) {
        synchronized (TOKEN_FETCH_LOCK) {
            // double-check 缓存
            String token = tokenCacheStrategy.get(cacheKey);
            if (token != null) {
                TokenStatus status = tokenRefreshExecutor.checkTokenStatus(token);
                if (status == TokenStatus.VALID || status == TokenStatus.EXPIRING_SOON) {
                    return token;
                }
            }
            return fetchTokenFromServer(cacheKey, securityContext);
        }
    }
}
