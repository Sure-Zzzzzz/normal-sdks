package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant;

/**
 * Simple XFF Capture Audit Elasticsearch Persistence Provider 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureAuditEsPersistenceProviderConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.http.xff.capture.persistence.elasticsearch";
    /**
     * 启用配置名称。
     */
    public static final String CONFIG_ENABLE = "enable";
    /**
     * 配置启用值。
     */
    public static final String CONFIG_VALUE_TRUE = "true";
    /**
     * 固定逻辑索引。
     */
    public static final String AUDIT_WRITE_INDEX = "xff-capture-audit";

    private SimpleXffCaptureAuditEsPersistenceProviderConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
