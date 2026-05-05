package io.github.surezzzzzz.sdk.cache;

import java.util.Optional;

/**
 * L2 缓存预刷新处理器
 *
 * <p>业务侧实现此接口并注册为 Spring Bean，当 L2 条目进入预刷新窗口时，
 * SmartCacheManager 异步调用 {@link #reload} 获取新值写回 L2（同时更新 L1）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>框架只负责触发时机，不感知业务数据如何加载</li>
 *   <li>多个 handler 可共存，通过 {@link #support} 按 cacheName 路由</li>
 *   <li>{@link #reload} 应是幂等的，失败时仅打 warn 日志，不影响当前请求</li>
 *   <li>无 handler 注册时静默跳过，不报错</li>
 *   <li>覆盖 {@link #needPreload} 可完全替代框架的 TTL 查询，避免额外 Redis IO</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface CachePreloadHandler {

    /**
     * 是否处理指定 cacheName 的预刷新
     *
     * @param cacheName 缓存名称
     * @return true 表示由此 handler 处理
     */
    boolean support(String cacheName);

    /**
     * 重新加载数据
     *
     * <p>返回值将写回 L2（同时更新 L1）。返回 null 时不写入。
     *
     * @param cacheName 缓存名称
     * @param key       缓存 key
     * @return 新值，null 表示不更新
     */
    Object reload(String cacheName, String key);

    /**
     * 是否需要触发 preload（可选覆盖）
     *
     * <p>默认返回 {@link Optional#empty()}，表示交给框架查 L2 TTL 决定（接受一次额外 Redis IO）。
     * 业务侧可覆盖此方法，返回 {@code Optional.of(true/false)} 完全替代框架的 TTL 查询。
     *
     * <p>适用场景：业务侧有更高效的判断方式时覆盖，例如 token manager 直接解析 JWT：
     * <pre>{@code
     * @Override
     * public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
     *     TokenStatus status = tokenRefreshExecutor.checkTokenStatus((String) cachedValue);
     *     if (status == TokenStatus.EXPIRING_SOON) return Optional.of(true);
     *     if (status == TokenStatus.VALID) return Optional.of(false);
     *     return Optional.empty();
     * }
     * }</pre>
     *
     * @param cacheName   缓存名称
     * @param key         缓存 key
     * @param cachedValue L2 命中的值，业务侧可直接用它判断（如解析 JWT）
     * @return empty 表示交给框架查 TTL 判断，否则直接使用返回值
     */
    default Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
        return Optional.empty();
    }
}
