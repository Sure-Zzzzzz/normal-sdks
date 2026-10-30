package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;

/**
 * 吊销资料新鲜度政策：OPTIONAL（不要求定期导入）或 REQUIRED（必须 maxAgeSeconds）。
 *
 * <p>REQUIRED 模式在有效资料缺失或 issuedAt+maxAgeSeconds &lt;= effectiveNow 时拒绝；
 * 不从启动参数补值。</p>
 *
 * @author surezzzzzz
 */
public final class RevocationPolicy {

    /** 政策种类。 */
    public enum Mode {
        OPTIONAL("OPTIONAL"), REQUIRED("REQUIRED");

        private final String code;

        Mode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Mode mode;
    private final Long maxAgeSeconds;

    private RevocationPolicy(Mode mode, Long maxAgeSeconds) {
        if (mode == Mode.REQUIRED) {
            if (maxAgeSeconds == null || maxAgeSeconds < 1) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
        } else if (maxAgeSeconds != null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        this.mode = mode;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public static RevocationPolicy optional() {
        return new RevocationPolicy(Mode.OPTIONAL, null);
    }

    public static RevocationPolicy required(long maxAgeSeconds) {
        return new RevocationPolicy(Mode.REQUIRED, maxAgeSeconds);
    }

    public Mode getMode() {
        return mode;
    }

    /** REQUIRED 专用；OPTIONAL 为 null。 */
    public Long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }
}
