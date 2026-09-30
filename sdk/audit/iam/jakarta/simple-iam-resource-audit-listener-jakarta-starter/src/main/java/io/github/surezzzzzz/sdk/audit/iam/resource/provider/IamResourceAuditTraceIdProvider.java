package io.github.surezzzzzz.sdk.audit.iam.resource.provider;

/**
 * IAM 资源审计 TraceId 提供者。
 *
 * @author surezzzzzz
 */
public interface IamResourceAuditTraceIdProvider {

    /**
     * 返回当前调用链追踪标识。
     *
     * @return 追踪标识；无法获取时返回 null
     */
    String getTraceId();
}
