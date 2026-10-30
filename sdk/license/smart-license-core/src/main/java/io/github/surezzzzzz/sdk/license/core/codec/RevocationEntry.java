package io.github.surezzzzzz.sdk.license.core.codec;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;

/**
 * 吊销条目：{targetKind, targetId, revokedAt}（协议 2 吊销分片内条目）。
 *
 * <p>目标是编号或业务 kid，不携带理由、操作人员、原始设备指纹或客户展示资料。</p>
 *
 * @author surezzzzzz
 */
public final class RevocationEntry {

    /** 吊销目标类型。 */
    public enum TargetKind {
        CREDENTIAL("CREDENTIAL"),
        GRANT("GRANT"),
        ACTIVATION("ACTIVATION"),
        ASSIGNMENT("ASSIGNMENT"),
        SIGNING_KEY("SIGNING_KEY");

        private final String code;

        TargetKind(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final TargetKind targetKind;
    private final String targetId;
    private final long revokedAt;

    private RevocationEntry(TargetKind targetKind, String targetId, long revokedAt) {
        Protocol2Credential.validateId(targetId, "targetId");
        Protocol2Credential.validateEpoch(revokedAt, "revokedAt");
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.revokedAt = revokedAt;
    }

    public static RevocationEntry of(TargetKind targetKind, String targetId, long revokedAt) {
        if (targetKind == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return new RevocationEntry(targetKind, targetId, revokedAt);
    }

    public TargetKind getTargetKind() {
        return targetKind;
    }

    public String getTargetId() {
        return targetId;
    }

    public long getRevokedAt() {
        return revokedAt;
    }

    /** 条目去重键：类型+编号。 */
    public String deduplicationKey() {
        return targetKind.getCode() + ":" + targetId;
    }
}
