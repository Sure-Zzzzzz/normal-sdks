package io.github.surezzzzzz.sdk.audit.license.provider;

/**
 * License 审计链路追踪ID提供者
 *
 * <p>宿主可实现此接口为审计记录附加当前链路追踪ID；缺省为空。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface LicenseAuditTraceIdProvider {

    /**
     * 获取链路追踪ID
     *
     * @return 链路追踪ID
     */
    String getTraceId();
}
