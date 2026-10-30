package io.github.surezzzzzz.sdk.license.core.constant;

/**
 * Core 2 错误分类编码。
 *
 * <p>分类面向拒绝原因的安全归属（协议/结构/类型/规则/密码学/能力），不携带可定位到客户的明文输入；商业拒绝属于业务结果不属于错误。</p>
 *
 * @author surezzzzzz
 */
public enum ErrorCode {

    /** Compact JWS 编码形态非法：段数、空白、padding、非法字符。 */
    PROTOCOL_ENCODING("LICENSE_PROTOCOL_001"),

    /** header/payload JSON 解析失败、重复键、超深、非有限数、UTF-8 非法。 */
    PROTOCOL_JSON("LICENSE_PROTOCOL_002"),

    /** 协议字段缺失、未知字段或取值越界。 */
    PROTOCOL_FIELD("LICENSE_PROTOCOL_003"),

    /** 时间字段非法或时间关系不成立。 */
    PROTOCOL_TIME("LICENSE_PROTOCOL_004"),

    /** 对象联合结构非法组合（部署混人员字段、人员带激活等）。 */
    PROTOCOL_BINDING("LICENSE_PROTOCOL_005"),

    /** 验签失败：错误签名、未知 kid、错用途、外来公钥。 */
    CRYPTO_SIGNATURE("LICENSE_CRYPTO_001"),

    /** 签名段编码非法（DER/JOSE 混淆、长度不符）。 */
    CRYPTO_ENCODING("LICENSE_CRYPTO_002"),

    /** 规则树结构、引用、类型或单位非法。 */
    RULE_STRUCTURE("LICENSE_RULE_001"),

    /** 规则求值缺失必需事实或事实类型/范围不符。 */
    RULE_FACT_MISSING("LICENSE_RULE_002"),

    /** 规则预算超限，确定拒绝。 */
    RULE_BUDGET("LICENSE_RULE_003"),

    /** 缺少或不支持声明的能力。 */
    CAPABILITY_UNSUPPORTED("LICENSE_CAPABILITY_001"),
    /** 凭据/授权/激活/资格已被不可撤回吊销（区别于签名失败）。 */
    CREDENTIAL_REVOKED("LICENSE_STATE_001"),
    /** REQUIRED 模式下吊销资料缺失或过旧（新鲜度不满足）。 */
    REVOCATION_STALE("LICENSE_STATE_002");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 按编码解析，未知编码返回 null；不抛异常，供防御性展示使用。 */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        ErrorCode[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }

    public String getDescription() {
        switch (this) {
            case PROTOCOL_ENCODING:
                return "JWS 编码形态非法";
            case PROTOCOL_JSON:
                return "JSON 解析非法";
            case PROTOCOL_FIELD:
                return "协议字段非法";
            case PROTOCOL_TIME:
                return "时间字段非法";
            case PROTOCOL_BINDING:
                return "对象绑定组合非法";
            case CRYPTO_SIGNATURE:
                return "验签失败";
            case CRYPTO_ENCODING:
                return "签名编码非法";
            case RULE_STRUCTURE:
                return "规则结构非法";
            case RULE_FACT_MISSING:
                return "必需事实缺失";
            case RULE_BUDGET:
                return "规则预算超限";
            case CAPABILITY_UNSUPPORTED:
                return "能力不支持";
            case CREDENTIAL_REVOKED:
                return "凭据或授权已吊销";
            case REVOCATION_STALE:
                return "吊销资料过旧或缺失";
            default:
                return "未知错误";
        }
    }
}
