package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;

/**
 * AKSK 用户上下文 ThreadLocal 持有者
 *
 * <p>在 AkskAccessEvent 发布时（认证成功，与请求同线程）存入用户信息，
 * 供后续 ES 查询审计时读取。请求结束后必须调用 {@link #clear()} 清理，
 * 避免线程池复用导致内存泄漏或数据污染。
 *
 * <p>推荐通过 {@link AkskContextClearInterceptor} 在 afterCompletion 中自动清理。
 *
 * @author surezzzzzz
 * @since 1.0.0
 * @see AkskAccessEventUserListener
 * @see AkskContextEsAuditUserProvider
 * @see AkskContextClearInterceptor
 */
public final class AkskContextHolder {

    private static final ThreadLocal<AkskAccessEvent> CONTEXT = new ThreadLocal<>();

    public static void set(AkskAccessEvent event) {
        CONTEXT.set(event);
    }

    public static AkskAccessEvent get() {
        return CONTEXT.get();
    }

    /**
     * 清理 ThreadLocal，防止内存泄漏。
     *
     * <p>必须在每次请求结束后调用（通常由 HandlerInterceptor.afterCompletion 触发）。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    private AkskContextHolder() {
    }
}
