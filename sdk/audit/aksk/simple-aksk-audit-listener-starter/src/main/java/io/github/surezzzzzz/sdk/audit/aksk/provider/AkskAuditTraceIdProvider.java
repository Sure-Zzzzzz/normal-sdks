package io.github.surezzzzzz.sdk.audit.aksk.provider;

/**
 * AKSK 审计链路追踪ID提供者
 *
 * <p>业务可以实现此接口来提供当前请求的链路追踪ID。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface AkskAuditTraceIdProvider {
    /**
     * 获取链路追踪ID
     */
    String getTraceId();
}
