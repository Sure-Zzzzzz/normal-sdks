package io.github.surezzzzzz.sdk.license.core.constant;

/**
 * License Core 2 协议与安全预算常量。
 *
 * <p>全部为协议支持边界，不从客户授权、模型或启动参数放宽；任何超限输入按对应错误分类确定拒绝。</p>
 *
 * @author surezzzzzz
 */
public final class SmartLicenseCoreConstant {

    private SmartLicenseCoreConstant() {
    }

    /** 签名协议版本，当前为 2；不兼容 v1 文件，不自动降级。 */
    public static final int PROTOCOL_VERSION = 2;

    /** 通用规则协议名。 */
    public static final String RULE_PROTOCOL_NAME = "license-condition";

    /** 通用规则协议版本。 */
    public static final int RULE_PROTOCOL_VERSION = 1;

    /** Compact JWS 恰好三段：header、payload、signature。 */
    public static final int JWS_SEGMENT_COUNT = 3;

    /** protected header 解码后字节上限 4096。 */
    public static final int HEADER_MAX_BYTES = 4096;

    /** payload 解码后字节上限 512 KiB。 */
    public static final int PAYLOAD_MAX_BYTES = 524288;

    /** 整个 Compact JWS 字节上限约 694 KiB（三段原始文本）。 */
    public static final int JWS_MAX_BYTES = 710000;

    /** JSON 对象/数组嵌套深度上限。 */
    public static final int JSON_MAX_DEPTH = 64;

    /** JOSE ES256 签名段固定 64 字节（R||S 各 32）。 */
    public static final int ES256_SIGNATURE_BYTES = 64;

    /** P-256 未压缩点与 SPKI 公钥字节长度。 */
    public static final int P256_POINT_BYTES = 65;

    /** UTC Epoch 秒取值上限（9999-12-31），防止毫秒时间或溢出值混入。 */
    public static final long EPOCH_SECONDS_MAX = 253402300799L;

    /** 编号类字段（iss/aud/customerId/grantId/activationId/subjectRef 各项等）UTF-8 字节上限。 */
    public static final int IDENTIFIER_MAX_BYTES = 128;

    /** 每命名空间字段数上限。 */
    public static final int FIELDS_PER_NAMESPACE_MAX = 128;

    /** 字段名最大长度（ASCII）。 */
    public static final int FIELD_NAME_MAX_LENGTH = 64;

    /** 文本字段/集合元素 Unicode 码点上限。 */
    public static final int TEXT_CODEPOINTS_MAX = 256;

    /** 集合元素数上限。 */
    public static final int SET_ITEMS_MAX = 256;

    /** 每模型动作数上限。 */
    public static final int ACTIONS_PER_MODEL_MAX = 256;

    /** 动作名最大长度（ASCII）。 */
    public static final int ACTION_NAME_MAX_LENGTH = 128;

    /** 每动作条件节点数上限。 */
    public static final int NODES_PER_ACTION_MAX = 256;

    /** 条件树深度上限。 */
    public static final int CONDITION_DEPTH_MAX = 16;

    /** 每逻辑节点子项上限。 */
    public static final int CHILDREN_PER_LOGIC_NODE_MAX = 32;

    /** 每动作引用字段上限。 */
    public static final int REFS_PER_ACTION_MAX = 128;

    /** 整模型条件节点总数上限。 */
    public static final int MODEL_NODES_MAX = 4096;

    /** 单次判定预算单位上限（节点一单位、集合逐成员一单位）。 */
    public static final int EVALUATION_BUDGET_UNITS = 4096;

    /** requiredCapabilities 项数上限与每项长度上限。 */
    public static final int CAPABILITIES_MAX = 32;
    public static final int CAPABILITY_MAX_LENGTH = 128;

    /** 凭据内额度分配项上限。 */
    public static final int QUOTA_ALLOCATIONS_MAX = 128;

    /** 凭据/租约内占用引用上限。 */
    public static final int RESERVATION_REFERENCES_MAX = 256;

    /** 实名指标引用上限。 */
    public static final int NAMED_SEAT_REFERENCES_MAX = 128;

    /** 吊销分片每片条目上限与单批分片数上限。 */
    public static final int REVOCATION_ENTRIES_PER_PART_MAX = 4096;
    public static final int REVOCATION_PARTS_MAX = 256;

    /** 时钟政策 skew 与回退容忍的秒上限。 */
    public static final int CLOCK_POLICY_SECONDS_MAX = 300;
}
