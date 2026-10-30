package io.github.surezzzzzz.sdk.license.core.codec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.CredentialKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 协议 2 凭据载荷（离线/混合，不含在线判定响应）。
 *
 * <p>顶层未知字段拒绝；requiredCapabilities 无重复；subjectBinding 为严格联合结构。
 * 纯 ONLINE 不发此载荷。</p>
 *
 * @author surezzzzzz
 */
public final class Protocol2Credential {

    @JsonProperty("protocolVersion")
    private final int protocolVersion;

    @JsonProperty("credentialKind")
    private final CredentialKind credentialKind;

    @JsonProperty("jti")
    private final String jti;

    @JsonProperty("iss")
    private final String issuerId;

    @JsonProperty("aud")
    private final String productId;

    @JsonProperty("customerId")
    private final String customerId;

    @JsonProperty("grantId")
    private final String grantId;

    @JsonProperty("grantRevision")
    private final long grantRevision;

    @JsonProperty("grantStateVersion")
    private final long grantStateVersion;

    @JsonProperty("iat")
    private final long issuedAt;

    @JsonProperty("nbf")
    private final long notBefore;

    @JsonProperty("model")
    private final Map<String, Object> modelSnapshot;

    @JsonProperty("rights")
    private final Map<String, Object> rights;

    @JsonProperty("requiredCapabilities")
    private final List<String> requiredCapabilities;

    @JsonCreator
    public Protocol2Credential(
            @JsonProperty("protocolVersion") int protocolVersion,
            @JsonProperty("credentialKind") CredentialKind credentialKind,
            @JsonProperty("jti") String jti,
            @JsonProperty("iss") String issuerId,
            @JsonProperty("aud") String productId,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("grantId") String grantId,
            @JsonProperty("grantRevision") long grantRevision,
            @JsonProperty("grantStateVersion") long grantStateVersion,
            @JsonProperty("iat") long issuedAt,
            @JsonProperty("nbf") long notBefore,
            @JsonProperty("model") Map<String, Object> modelSnapshot,
            @JsonProperty("rights") Map<String, Object> rights,
            @JsonProperty("requiredCapabilities") List<String> requiredCapabilities) {
        if (protocolVersion != SmartLicenseCoreConstant.PROTOCOL_VERSION) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (credentialKind == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        validateId(jti, "jti");
        validateId(issuerId, "iss");
        validateId(productId, "aud");
        validateId(customerId, "customerId");
        validateId(grantId, "grantId");
        if (grantRevision < 1 || grantStateVersion < 1) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        validateEpoch(issuedAt, "iat");
        validateEpoch(notBefore, "nbf");
        if (requiredCapabilities != null && requiredCapabilities.size() > SmartLicenseCoreConstant.CAPABILITIES_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        this.protocolVersion = protocolVersion;
        this.credentialKind = credentialKind;
        this.jti = jti;
        this.issuerId = issuerId;
        this.productId = productId;
        this.customerId = customerId;
        this.grantId = grantId;
        this.grantRevision = grantRevision;
        this.grantStateVersion = grantStateVersion;
        this.issuedAt = issuedAt;
        this.notBefore = notBefore;
        this.modelSnapshot = modelSnapshot;
        this.rights = rights;
        this.requiredCapabilities = requiredCapabilities;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public CredentialKind getCredentialKind() {
        return credentialKind;
    }

    public String getJti() {
        return jti;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getGrantId() {
        return grantId;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public long getGrantStateVersion() {
        return grantStateVersion;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getNotBefore() {
        return notBefore;
    }

    public Map<String, Object> getModelSnapshot() {
        return modelSnapshot;
    }

    public Map<String, Object> getRights() {
        return rights;
    }

    public List<String> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    static void validateId(String value, String field) {
        if (value == null || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.IDENTIFIER_MAX_BYTES
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD,
                    ErrorMessage.PROTOCOL_FIELD_INVALID + "：" + field);
        }
    }

    static void validateEpoch(long value, String field) {
        if (value < 0 || value > SmartLicenseCoreConstant.EPOCH_SECONDS_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_TIME,
                    ErrorMessage.PROTOCOL_TIME_INVALID + "：" + field);
        }
    }
}
