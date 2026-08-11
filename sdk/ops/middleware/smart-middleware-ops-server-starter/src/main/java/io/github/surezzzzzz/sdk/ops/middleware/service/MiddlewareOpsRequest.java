package io.github.surezzzzzz.sdk.ops.middleware.service;

/**
 * 类型化运维能力请求。
 *
 * @author surezzzzzz
 */
public interface MiddlewareOpsRequest {

    /**
     * 获取固定能力标识。
     *
     * @return capability
     */
    MiddlewareOpsCapability getCapability();

    /**
     * 获取目标数据源。
     *
     * @return datasource key，可为空
     */
    String getDatasourceKey();

    /**
     * 获取资源范围的安全投影。
     *
     * @return 安全资源范围
     */
    String getResourceScope();

    /**
     * 是否需要发布审计事件。
     *
     * @return 是否需要审计
     */
    default boolean isAuditRequired() {
        return true;
    }
}
