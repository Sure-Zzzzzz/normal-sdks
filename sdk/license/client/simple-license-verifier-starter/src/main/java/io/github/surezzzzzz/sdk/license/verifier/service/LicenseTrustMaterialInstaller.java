package io.github.surezzzzzz.sdk.license.verifier.service;

import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationPackageCodec;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import io.github.surezzzzzz.sdk.license.core.verify.AtomicRevocationStore;

/**
 * 将已通过传输响应校验的完整信任资料交给 Verifier。
 *
 * <p>吊销清单必须先用已登记 REVOCATION 公钥完整验签，再原子安装；响应中的公钥 JSON
 * 不会自动成为信任根，信任根更新仍需宿主受控安装 TrustStore。</p>
 *
 * @author surezzzzzz
 */
public class LicenseTrustMaterialInstaller {

    private final TrustStore trustStore;
    private final AtomicRevocationStore revocationStore;

    public LicenseTrustMaterialInstaller(TrustStore trustStore, AtomicRevocationStore revocationStore) {
        this.trustStore = trustStore;
        this.revocationStore = revocationStore;
    }

    /** 整批核验失败保留旧仓；不会先清空再安装。 */
    public void install(String issuerId, String productId, TrustMaterialSnapshot snapshot,
                        long nowEpochSeconds) {
        if (snapshot == null || snapshot.getManifestJws() == null) {
            throw new IllegalArgumentException("信任资料缺少 manifest");
        }
        RevocationPackageCodec.VerifiedPackage verified = RevocationPackageCodec.verify(
                snapshot.getManifestJws(), snapshot.getRevocationPartJws(), issuerId, productId,
                trustStore, nowEpochSeconds);
        if (verified.getSequence() != snapshot.getSequence()
                || verified.getIssuedAt() != snapshot.getIssuedAt()
                || !verified.getSnapshotId().equals(snapshot.getSnapshotId())) {
            throw new IllegalArgumentException("信任资料响应元数据与签名批次不一致");
        }
        revocationStore.install(verified);
    }
}
