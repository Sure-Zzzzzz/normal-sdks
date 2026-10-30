package io.github.surezzzzzz.sdk.license.core.spi;

/**
 * 签名服务调用失败的业务语义异常：实现方以 reasonCode 区分失败类别，
 * 签发侧据此落 UNCERTAIN 的 reasonCategory（不重试、不盲发）。
 *
 * <p>消息内容不得携带钥引用等敏感材料（keyRef 由实现方脱敏后进日志）。</p>
 *
 * @author surezzzzzz
 */
public class SignerCallException extends RuntimeException {

    /** 档案不存在（登记被清理/编号错传）。 */
    public static final String PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND";

    /** 档案非在用（RETIRED 档案禁止新签名）。 */
    public static final String PROFILE_NOT_ACTIVE = "PROFILE_NOT_ACTIVE";

    /** 签名服务返回的钥引用/版本/算法与档案不符（配置漂移或错钥，必须人工核查）。 */
    public static final String KEY_MATERIAL_MISMATCH = "KEY_MATERIAL_MISMATCH";

    /** 签名服务不可达/不可用（网络、认证、限流；恢复后受控重试）。 */
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private final String reasonCode;

    public SignerCallException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public SignerCallException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
