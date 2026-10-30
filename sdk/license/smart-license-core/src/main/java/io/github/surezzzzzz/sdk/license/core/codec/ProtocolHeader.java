package io.github.surezzzzzz.sdk.license.core.codec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 协议 2 Protected Header：恰含 alg/kid/typ 三字段，无第四字段。
 *
 * <p>typ 区分五种文件用途：license+jwt / license-lease+jwt / license-revocations+jwt /
 * license-revocation-manifest+jwt / license-state-recovery+jwt。</p>
 *
 * @author surezzzzzz
 */
public final class ProtocolHeader {

    /** 离线凭据。 */
    public static final String TYP_CREDENTIAL = "license+jwt";

    /** 混合租约。 */
    public static final String TYP_LEASE = "license-lease+jwt";

    /** 吊销分片。 */
    public static final String TYP_REVOCATION = "license-revocations+jwt";

    /** 吊销批次清单。 */
    public static final String TYP_REVOCATION_MANIFEST = "license-revocation-manifest+jwt";

    /** 状态恢复。 */
    public static final String TYP_STATE_RECOVERY = "license-state-recovery+jwt";

    @JsonProperty("alg")
    private final String algorithm;

    @JsonProperty("kid")
    private final String kid;

    @JsonProperty("typ")
    private final String typ;

    @JsonCreator
    public ProtocolHeader(@JsonProperty("alg") String algorithm,
                          @JsonProperty("kid") String kid,
                          @JsonProperty("typ") String typ) {
        if (!"ES256".equals(algorithm)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (kid == null || kid.isEmpty()
                || kid.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.IDENTIFIER_MAX_BYTES
                || kid.codePoints().anyMatch(Character::isISOControl)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (!isValidTyp(typ)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        this.algorithm = algorithm;
        this.kid = kid;
        this.typ = typ;
    }

    public static boolean isValidTyp(String typ) {
        return TYP_CREDENTIAL.equals(typ) || TYP_LEASE.equals(typ) || TYP_REVOCATION.equals(typ)
                || TYP_REVOCATION_MANIFEST.equals(typ) || TYP_STATE_RECOVERY.equals(typ);
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getKid() {
        return kid;
    }

    public String getTyp() {
        return typ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProtocolHeader)) {
            return false;
        }
        ProtocolHeader that = (ProtocolHeader) other;
        return algorithm.equals(that.algorithm) && kid.equals(that.kid) && typ.equals(that.typ);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithm, kid, typ);
    }
}
