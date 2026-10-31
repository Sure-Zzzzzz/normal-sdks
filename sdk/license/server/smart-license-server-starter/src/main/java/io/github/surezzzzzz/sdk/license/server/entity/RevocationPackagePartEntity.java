package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;
import javax.persistence.Table;

/** 吊销包签名分片；按 package/index 唯一，保存原始 JWS 字节语义。 */
@Entity
@Table(name = "license_revocation_package_part")
@IdClass(RevocationPackagePartId.class)
public class RevocationPackagePartEntity {

    @Id
    @Column(name = "package_id", length = 128, nullable = false)
    private String packageId;
    @Id
    @Column(name = "part_index", nullable = false)
    private int partIndex;
    @Lob
    @Column(name = "part_jws", nullable = false)
    private String partJws;
    @Column(name = "part_sha256", length = 64, nullable = false, columnDefinition = "char(64)")
    private String partSha256;
    @Column(name = "entry_count", nullable = false)
    private int entryCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RevocationPackagePartEntity() { }

    public RevocationPackagePartEntity(String packageId, int partIndex, String partJws,
                                       String partSha256, int entryCount) {
        this.packageId = packageId;
        this.partIndex = partIndex;
        this.partJws = partJws;
        this.partSha256 = partSha256;
        this.entryCount = entryCount;
        this.createdAt = Instant.now();
    }

    public String getPackageId() { return packageId; }
    public int getPartIndex() { return partIndex; }
    public String getPartJws() { return partJws; }
    public String getPartSha256() { return partSha256; }
    public int getEntryCount() { return entryCount; }
    public Instant getCreatedAt() { return createdAt; }
}
