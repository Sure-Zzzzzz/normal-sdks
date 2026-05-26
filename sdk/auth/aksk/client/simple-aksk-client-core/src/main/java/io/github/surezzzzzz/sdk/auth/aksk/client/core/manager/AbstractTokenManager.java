package io.github.surezzzzzz.sdk.auth.aksk.client.core.manager;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.strategy.TokenCacheStrategy;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * Token Manager 抽象基类
 *
 * <p>使用 <b>Template Method 模式</b>封装 {@link #getToken()} 的通用流程：
 * <ol>
 *   <li>获取 securityContext</li>
 *   <li>生成 cacheKey</li>
 *   <li>查缓存：命中则直接返回</li>
 *   <li>缓存未命中 → 加锁同步获取新 Token</li>
 * </ol>
 *
 * <p>子类只需实现 {@link #fetchTokenWithLock} 提供各自的加锁策略：
 * <ul>
 *   <li>{@code RedisTokenManager}：分布式锁（适合多实例部署）</li>
 *   <li>{@code HttpSessionTokenManager}：JVM 本地锁（适合单实例部署）</li>
 * </ul>
 *
 * <p>Token 有效性由缓存 TTL 保证（{@link TokenCacheStrategy#calculateTtl} 提前过期），
 * 不依赖解析 Token 内容，兼容 JWE 加密格式。
 *
 * @author surezzzzzz
 */
@Slf4j
public abstract class AbstractTokenManager implements TokenManager {

    protected final SimpleAkskClientCoreProperties coreProperties;
    protected final TokenCacheStrategy tokenCacheStrategy;
    protected final SecurityContextProvider securityContextProvider;
    protected final TokenRefreshExecutor tokenRefreshExecutor;

    protected AbstractTokenManager(
            SimpleAkskClientCoreProperties coreProperties,
            TokenCacheStrategy tokenCacheStrategy,
            SecurityContextProvider securityContextProvider,
            TaskRetryExecutor retryExecutor) {
        this.coreProperties = coreProperties;
        this.tokenCacheStrategy = tokenCacheStrategy;
        this.securityContextProvider = securityContextProvider;
        this.tokenRefreshExecutor = new TokenRefreshExecutor(coreProperties, retryExecutor);
    }

    /**
     * 获取 Token（Template Method，子类不可覆盖）
     *
     * <p>缓存命中直接返回；缓存未命中时同步获取。
     * Token 有效性由缓存 TTL 保证（{@link TokenCacheStrategy#calculateTtl} 提前过期），
     * 不依赖解析 Token 内容，兼容 JWE 加密格式。
     *
     * @return Access Token
     */
    @Override
    public final String getToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        String cachedToken = tokenCacheStrategy.get(cacheKey);

        if (cachedToken != null) {
            log.debug("Token cache hit: key={}", cacheKey);
            return cachedToken;
        }

        log.debug("Token cache miss, fetching: key={}", cacheKey);
        return fetchTokenWithLock(cacheKey, securityContext);
    }

    /**
     * 清除 Token 缓存
     */
    @Override
    public void clearToken() {
        String securityContext = securityContextProvider.getSecurityContext();
        String cacheKey = tokenCacheStrategy.generateCacheKey(securityContext);
        tokenCacheStrategy.remove(cacheKey);
        log.debug("Token cleared: key={}", cacheKey);
    }

    /**
     * 加锁获取 Token（子类实现各自的加锁策略）
     *
     * @param cacheKey        缓存 Key
     * @param securityContext 安全上下文
     * @return Access Token
     */
    protected abstract String fetchTokenWithLock(String cacheKey, String securityContext);

    /**
     * 从 OAuth2 Server 获取 Token 并写缓存（子类复用）
     *
     * @param cacheKey        缓存 Key
     * @param securityContext 安全上下文
     * @return Access Token
     */
    protected String fetchTokenFromServer(String cacheKey, String securityContext) {
        return tokenRefreshExecutor.fetchTokenFromServer(
                securityContext,
                (accessToken, expiresIn) -> {
                    tokenCacheStrategy.put(cacheKey, accessToken, expiresIn);
                    log.info("Token fetched and cached: key={}, expiresIn={}s", cacheKey, expiresIn);
                }
        );
    }
}
