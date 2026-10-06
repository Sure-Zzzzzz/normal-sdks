package io.github.surezzzzzz.sdk.audit.aksk.resource.provider;

/**
 * 可选审计追踪标识提供者；沿用旧线在异步消费线程调用的语义。
 *
 * @author surezzzzzz
 */
public interface AkskAuditTraceIdProvider {
    /**
     * 返回消费线程中可获得的追踪标识；不得依赖发布线程的请求上下文或线程局部变量。
     *
     * @return 追踪标识；无可用值时返回 null
     */
    String getTraceId();
}
