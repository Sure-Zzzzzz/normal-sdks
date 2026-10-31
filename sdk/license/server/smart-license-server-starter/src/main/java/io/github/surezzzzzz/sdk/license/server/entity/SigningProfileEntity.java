package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 签名档案（license_signing_profile）：产品×用途×kid 的签名钥映射的不可变历史记录。
 *
 * <p>协议暴露 kid，KMS keyRef/版本只存服务端（不进凭据、不进列表响应）。
 * 表不设 (productId, purpose) 唯一约束：轮换=旧档案 RETIRED 留验历史凭据、新档案 ACTIVE，
 * 历史行永久保留；“每产品每用途至多一个 ACTIVE”由登记事务（悲观锁+expectedRowVersion）
 * 与 schema 的 active_marker 生成列唯一键共同保证。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_signing_profile",
        indexes = @Index(name = "idx_license_signing_profile_lookup",
                columnList = "product_id, purpose, status"))
public class SigningProfileEntity {

    @Id
    @Column(name = "profile_id", length = 64, nullable = false)
    private String profileId;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    /** CREDENTIAL / REVOCATION / STATE_RECOVERY。 */
    @Column(name = "purpose", length = 32, nullable = false)
    private String purpose;

    /** 协议 kid（全局唯一不可改绑）。 */
    @Column(name = "kid", length = 128, nullable = false)
    private String kid;

    /** 签名服务侧钥引用（LicenseSigner SPI 消费，不出协议面）。 */
    @Column(name = "key_ref", length = 256, nullable = false)
    private String keyRef;

    /** 精确钥版本。 */
    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    /** 登记时固化的公钥 SPKI（hex，验证端信任快照的来源）。 */
    @Column(name = "registered_public_key_hex", nullable = false, length = 1024)
    private String registeredPublicKeyHex;

    /** STAGED / ACTIVE / RETIRED。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SigningProfileEntity() {
    }

    public SigningProfileEntity(String profileId, String productId, String purpose, String kid,
                                 String keyRef, int keyVersion, String registeredPublicKeyHex) {
        this.profileId = profileId;
        this.productId = productId;
        this.purpose = purpose;
        this.kid = kid;
        this.keyRef = keyRef;
        this.keyVersion = keyVersion;
        this.registeredPublicKeyHex = registeredPublicKeyHex;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProductId() {
        return productId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getKid() {
        return kid;
    }

    public String getKeyRef() {
        return keyRef;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public String getRegisteredPublicKeyHex() {
        return registeredPublicKeyHex;
    }

    public String getStatus() {
        return status;
    }

    public void retire() {
        this.status = "RETIRED";
        this.updatedAt = Instant.now();
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
