package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 信任域内已登记的公钥资料（协议 2 公钥信任条目）。
 *
 * <p>issuerId/productId/kid/用途/算法与 SPKI 公钥字节、单调信任版本；kid 全局唯一不可改绑，
 * 密钥用途是信任资料的一部分，不能从载荷推导。</p>
 *
 * @author surezzzzzz
 */
public final class TrustedPublicKey {

    /** 公钥用途：日常凭据、吊销、状态恢复三钥分离。 */
    public enum Purpose {
        CREDENTIAL("CREDENTIAL"),
        REVOCATION("REVOCATION"),
        STATE_RECOVERY("STATE_RECOVERY");

        private final String code;

        Purpose(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final String issuerId;
    private final String productId;
    private final String kid;
    private final Purpose purpose;
    private final long trustVersion;
    private final byte[] spki;

    private TrustedPublicKey(String issuerId, String productId, String kid, Purpose purpose,
                             long trustVersion, byte[] spki) {
        this.issuerId = issuerId;
        this.productId = productId;
        this.kid = kid;
        this.purpose = purpose;
        this.trustVersion = trustVersion;
        this.spki = spki.clone();
    }

    public static TrustedPublicKey of(String issuerId, String productId, String kid, Purpose purpose,
                                      long trustVersion, byte[] spki) {
        validateIdentifier(issuerId);
        validateIdentifier(productId);
        validateIdentifier(kid);
        if (issuerId == null || issuerId.isEmpty() || productId == null || productId.isEmpty()
                || kid == null || kid.isEmpty() || purpose == null || trustVersion < 1
                || spki == null || spki.length == 0) {
            throw new IllegalArgumentException("信任资料字段非法");
        }
        return new TrustedPublicKey(issuerId, productId, kid, purpose, trustVersion, spki);
    }

    /** 信任索引与签名头使用相同编号预算，避免非法索引在验签之前进入持久仓。 */
    private static void validateIdentifier(String value) {
        if (value == null || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.IDENTIFIER_MAX_BYTES
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(ErrorMessage.IDENTIFIER_INVALID);
        }
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getKid() {
        return kid;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public long getTrustVersion() {
        return trustVersion;
    }

    /** SPKI 公钥字节副本（防御性复制）。 */
    public byte[] getSpki() {
        return spki.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TrustedPublicKey)) {
            return false;
        }
        TrustedPublicKey that = (TrustedPublicKey) other;
        return trustVersion == that.trustVersion
                && issuerId.equals(that.issuerId)
                && productId.equals(that.productId)
                && kid.equals(that.kid)
                && purpose == that.purpose
                && Arrays.equals(spki, that.spki);
    }

    @Override
    public int hashCode() {
        int result = issuerId.hashCode();
        result = 31 * result + productId.hashCode();
        result = 31 * result + kid.hashCode();
        result = 31 * result + purpose.hashCode();
        result = 31 * result + (int) (trustVersion ^ (trustVersion >>> 32));
        result = 31 * result + Arrays.hashCode(spki);
        return result;
    }
}
