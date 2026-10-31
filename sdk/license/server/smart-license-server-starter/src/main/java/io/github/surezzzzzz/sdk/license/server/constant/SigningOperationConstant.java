package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 签发任务查询与冻结载荷的字段契约。
 *
 * @author surezzzzzz
 */
public final class SigningOperationConstant {

    public static final String GRANT_ID = "grantId";
    public static final String ACTIVATION_ID = "activationId";
    public static final String OPERATION_ID = "operationId";
    public static final String SUBJECT_BINDING = "subjectBinding";
    public static final String KIND = "kind";
    public static final int IDENTIFIER_MAX_LENGTH = 128;
    public static final String INVALID_DEPLOYMENT_BINDING = "部署凭据必须包含有效的激活编号";
    public static final String VERSION_CONFLICT = "签发任务版本或执行代数已变化，请回读后重试";
    public static final String RECOVERY_STATE_INVALID = "仅结果不明或显式允许接管的失联签名状态可恢复";
    public static final String INITIAL_STATE_INVALID = "首次签名仅接受待签名状态";
    public static final String CAS_UNSUPPORTED = "签发仓储未实现行版本与执行代数的条件更新";
    public static final String ABANDON_STATE_INVALID = "当前状态不可放弃（READY/终态永不退回）";
    public static final String INVALID_EXPECTED_VERSION = "expectedRowVersion 必须为非负整数";
    public static final String INVALID_EXPECTED_GENERATION = "expectedExecutionGeneration 必须为正整数";
    public static final String STALE_SIGNING_DISABLED = "宿主未配置失联签名接管窗口，禁止接管 SIGNING";
    public static final String STALE_SIGNING_NOT_EXPIRED = "签名任务尚未达到接管窗口或缺少可信调用时刻";
    public static final String TAKEOVER_EVIDENCE_REQUIRED = "接管 SIGNING 必须提供原执行代数和非空 reasonCategory";
    public static final String INVALID_REASON = "reasonCategory 必须为非空文本且不超过64个字符";
    public static final String INVALID_FROZEN_ENCODING = "冻结header和payload必须为规范的无填充Base64URL非空段";
    public static final String PAYLOAD_BUDGET_EXCEEDED = "凭据规模超协议预算，任务已REJECTED";
    public static final String INVALID_PAGE = "page 必须大于等于 1，size 必须在 1 到 100 之间";
    public static final String EXECUTION_UNAVAILABLE = "签发执行结果未确认，请回读任务状态";
    public static final String INVALID_IDEMPOTENCY = "幂等键必须非空且与授权组合后不超过128个字符";
    public static final int IDEMPOTENCY_MAX_LENGTH = 128;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int REASON_MAX_LENGTH = 64;

    private SigningOperationConstant() {
    }
}
