package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.annotation.SimpleAkskHttpSessionTokenManagerComponent;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpSession Token Manager
 * <p>
 * 基于 HttpSession 的 Token 管理器
 * <p>
 * 特性：
 * <ul>
 *   <li>无需额外依赖</li>
 *   <li>适用于单实例应用或多实例应用（每个实例维护自己的 Token）</li>
 *   <li>自动刷新过期 Token</li>
 *   <li>支持 Token 即将过期时异步刷新</li>
 * </ul>
 * <p>
 * 多实例部署说明：
 * <ul>
 *   <li>每个实例维护自己的 Token 缓存（基于 HttpSession）</li>
 *   <li>不同实例之间的 Token 不共享</li>
 *   <li>如需共享 Token，建议使用 simple-aksk-redis-token-manager</li>
 *   <li>可选配置 Session 粘性或分布式 Session（如 Spring Session + Redis）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskHttpSessionTokenManagerComponent
@Slf4j
public class HttpSessionTokenManager implements TokenManager {

    private final SimpleAkskClientCoreProperties coreProperties;
    private final TokenCacheStrategy tokenCacheStrategy;
    private final SecurityContextProvider securityContextProvider;
    private final TokenRefreshExecutor tokenRefreshExecutor;

    public HttpSessionTokenManager(
            SimpleAkskClientCoreProperties coreProperties,
            TokenCacheStrategy tokenCacheStrategy,
            SecurityContextProvider securityContextProvider,
            TaskRetryExecutor retryExecutor
    ) {
        this.coreProperties = coreProperties;
        this.tokenCacheStrategy = tokenCacheStrategy;
        this.securityContextProvider = securityContextProvider;
        // 实例化 TokenRefreshExecutor（不作为 Bean）
        this.tokenRefreshExecutor = new TokenRefreshExecutor(coreProperties, retryExecutor);
    }

    /**
     * Token 获取锁（防止同一 JVM 内并发获取）
     */
    private static final Object TOKEN_FETCH_LOCK = new Object();

    @Override
    public String getToken() {
        // 1. 获取 security_context
        String securityContext = securityContextProvider.getSecurityContext();

        // 2. 生成缓存 Key
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);

        // 3. 尝试从缓存获取
        String cachedToken = tokenCacheStrategy.get(cacheKey);
        if (cachedToken != null) {
            // 检查 Token 状态
            TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(cachedToken);

            switch (status) {
                case EXPIRED:
                case UNPARSABLE:
                    // Token 已过期或无法解析，重新获取
                    log.debug("Token expired or unparsable: key={}, status={}", cacheKey, status);
                    break;

                case EXPIRING_SOON:
                    // Token 即将过期，异步刷新
                    log.debug("Token expiring soon: key={}, triggering async refresh", cacheKey);
                    tokenRefreshExecutor.asyncRefreshToken(() -> fetchTokenWithLock(cacheKey, securityContext));
                    return cachedToken;

                case VALID:
                    // Token 有效，直接返回
                    log.debug("Token cache hit: key={}", cacheKey);
                    return cachedToken;
            }
        }

        // 4. 缓存未命中或已过期，使用本地锁获取新 Token
        return fetchTokenWithLock(cacheKey, securityContext);
    }

    @Override
    public void clearToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        tokenCacheStrategy.remove(cacheKey);
        log.debug("Token cleared: key={}", cacheKey);
    }

    /**
     * 使用本地锁获取 Token（防止同一 JVM 内并发获取）
     */
    private String fetchTokenWithLock(String cacheKey, String securityContext) {
        synchronized (TOKEN_FETCH_LOCK) {
            // Double-check 缓存
            String token = tokenCacheStrategy.get(cacheKey);
            if (token != null) {
                TokenRefreshExecutor.TokenStatus status = tokenRefreshExecutor.checkTokenStatus(token);
                if (status == TokenRefreshExecutor.TokenStatus.VALID || status == TokenRefreshExecutor.TokenStatus.EXPIRING_SOON) {
                    return token;
                }
            }

            // 从 OAuth2 Server 获取新 Token
            return fetchTokenFromServer(cacheKey, securityContext);
        }
    }

    /**
     * 从 OAuth2 Server 获取 Token
     */
    private String fetchTokenFromServer(String cacheKey, String securityContext) {
        return tokenRefreshExecutor.fetchTokenFromServer(
                securityContext,
                (accessToken, expiresIn) -> {
                    tokenCacheStrategy.put(cacheKey, accessToken, expiresIn);
                    log.info("Token fetched and cached: key={}, expiresIn={}s", cacheKey, expiresIn);
                }
        );
    }
}
