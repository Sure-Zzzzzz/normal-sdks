package io.github.surezzzzzz.sdk.license.core.constant;

/**
 * Core 2 安全错误消息（中文，编码关联 ErrorCode）。
 *
 * <p>消息只描述类别与约束，不含原始输入、编号值、设备指纹或签名材料。</p>
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
    }

    public static final String PROTOCOL_ENCODING_INVALID = "JWS 必须为无 padding 的三段 Base64URL 文本";
    public static final String PROTOCOL_JSON_INVALID = "JSON 解析非法：重复键、超深、非有限数或 UTF-8 非法均拒绝";
    public static final String PROTOCOL_FIELD_INVALID = "协议字段缺失、未知或取值越界";
    public static final String PROTOCOL_TIME_INVALID = "时间字段非法或时间关系不成立";
    public static final String PROTOCOL_BINDING_INVALID = "对象绑定组合非法：部署仅含激活，人员/服务仅含真实身份引用";
    public static final String CRYPTO_SIGNATURE_INVALID = "验签失败：签名、密钥标识、用途或信任域不匹配";
    public static final String CRYPTO_ENCODING_INVALID = "签名段必须为 64 字节 JOSE R||S 编码";
    public static final String RULE_STRUCTURE_INVALID = "规则树结构、引用、类型或单位非法";
    public static final String RULE_FACT_MISSING = "动作引用的必需事实缺失或类型不符";
    public static final String RULE_BUDGET_EXCEEDED = "规则节点或求值预算超限，确定拒绝";
    public static final String CAPABILITY_UNSUPPORTED = "声明的能力缺失或本机不支持";
    public static final String IDENTIFIER_INVALID = "编号必须为非空文本且不超过协议字节上限";
    public static final String SUBJECT_KIND_INVALID = "对象种类必须为 DEPLOYMENT/PERSON/SERVICE 之一";
    public static final String SUBJECT_REF_KIND_INVALID = "身份引用种类只允许 PERSON/SERVICE";
    public static final String CREDENTIAL_REVOKED = "凭据或其授权对象已被吊销";
    public static final String REVOCATION_STALE = "吊销资料缺失或已过政策新鲜度上限";
}
