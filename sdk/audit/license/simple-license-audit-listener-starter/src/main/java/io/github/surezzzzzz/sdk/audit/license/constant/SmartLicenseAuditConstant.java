package io.github.surezzzzzz.sdk.audit.license.constant;

/**
 * License 审计常量（2.0 新事件类型族；旧 1.0 四类型随领域重构废弃）
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
public final class SmartLicenseAuditConstant {

    /**
     * 客户授权创建事件类型。
     */
    public static final String EVENT_TYPE_GRANT_CREATED = "grant-created";
    /**
     * 授权管理状态变更事件类型。
     */
    public static final String EVENT_TYPE_GRANT_STATE_CHANGED = "grant-state-changed";
    /**
     * 离线凭据签发就绪事件类型。
     */
    public static final String EVENT_TYPE_CREDENTIAL_ISSUED = "credential-issued";
    /**
     * 吊销创建事件类型。
     */
    public static final String EVENT_TYPE_REVOCATION_CREATED = "revocation-created";
    /**
     * 条件模型发布事件类型。
     */
    public static final String EVENT_TYPE_MODEL_PUBLISHED = "model-published";
    /**
     * 角色协作计划与安全状态变更事件类型。
     */
    public static final String EVENT_TYPE_ROLE_PROJECTION_CHANGED = "role-projection-changed";

    /** 已提交额度操作观察；包含重放，不声称每次观察都新扣减。 */
    public static final String EVENT_TYPE_QUOTA_OPERATION_COMPLETED = "quota-operation-completed";

    /** 吊销包真实提交后的 READY/FAILED 终态。 */
    public static final String EVENT_TYPE_REVOCATION_PACKAGE_COMPLETED = "revocation-package-completed";

    private SmartLicenseAuditConstant() {
    }
}
