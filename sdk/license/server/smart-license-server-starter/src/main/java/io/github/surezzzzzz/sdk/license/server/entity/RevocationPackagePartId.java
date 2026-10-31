package io.github.surezzzzzz.sdk.license.server.entity;

import java.io.Serializable;

/** 吊销包分片复合主键。 */
public class RevocationPackagePartId implements Serializable {
    private String packageId;
    private int partIndex;
    public RevocationPackagePartId() { }
    public RevocationPackagePartId(String packageId, int partIndex) {
        this.packageId = packageId;
        this.partIndex = partIndex;
    }
    public String getPackageId() { return packageId; }
    public int getPartIndex() { return partIndex; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof RevocationPackagePartId)) return false;
        RevocationPackagePartId that = (RevocationPackagePartId) other;
        return partIndex == that.partIndex && (packageId == null ? that.packageId == null : packageId.equals(that.packageId));
    }
    @Override public int hashCode() { return 31 * (packageId == null ? 0 : packageId.hashCode()) + partIndex; }
}
