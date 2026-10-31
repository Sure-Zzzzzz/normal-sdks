package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 管理面请求校验的安全消息，不携带原始输入。
 *
 * @author surezzzzzz
 */
public final class LicenseValidationConstant {

    public static final String UNKNOWN_VALIDITY_FIELD = "期限含未声明字段";
    public static final String VALIDITY_KIND_TYPE = "期限 kind 必须为字符串";
    public static final String PERPETUAL_DEADLINE = "永久期限不得携带截止时刻";
    public static final String VALIDITY_SECOND_TYPE = "期限时刻必须为整数秒：";
    public static final String BATCH_SIZE_RANGE = "batchSize 必须在 1 到 100 之间";
    public static final String RIGHTS_INVALID = "权益值不满足模型的类型、范围、长度、枚举或必填约束";
    public static final String OFFLINE_ISSUANCE_ONLY = "离线凭据签发仅支持 OFFLINE 授权";
    public static final String DECISION_DEPENDENCY_UNAVAILABLE = "在线判定依赖暂不可用";
    public static final String GRANT_ROW_VERSION_INVALID = "授权版本必须为非负整数";
    public static final String GRANT_STATE_CONFLICT = "当前授权状态不允许该变更";
    public static final String PRODUCT_CODE_CONFLICT = "产品编码在该签发方下已存在";

    private LicenseValidationConstant() {
    }
}
