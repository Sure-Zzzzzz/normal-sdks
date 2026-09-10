package io.github.surezzzzzz.sdk.audit.iam.resource.provider;

/**
 * IAM 资源访问审计链路追踪 ID 提供者
 *
 * <p>业务可以实现此接口来提供当前请求的链路追踪 ID；未提供时审计记录的 traceId 为 null。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface IamResourceAuditTraceIdProvider {

    /**
     * 获取链路追踪 ID
     *
     * @return 链路追踪 ID，无上下文时返回 null
     */
    String getTraceId();
}
