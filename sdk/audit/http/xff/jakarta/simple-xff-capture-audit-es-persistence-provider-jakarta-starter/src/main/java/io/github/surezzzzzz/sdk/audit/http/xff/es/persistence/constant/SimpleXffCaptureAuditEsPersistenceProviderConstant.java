package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant;

/**
 * XFF Capture 审计 Elasticsearch Provider 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureAuditEsPersistenceProviderConstant {

    /**
     * 模块配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.http.xff.capture.persistence.elasticsearch";
    /**
     * 启用配置名称。
     */
    public static final String CONFIG_ENABLE = "enable";
    /**
     * 启用配置值。
     */
    public static final String CONFIG_VALUE_TRUE = "true";
    /**
     * Route 解析的固定逻辑索引。
     */
    public static final String AUDIT_WRITE_INDEX = "xff-capture-audit";

    private SimpleXffCaptureAuditEsPersistenceProviderConstant() {
    }
}
