package io.github.surezzzzzz.sdk.license.client.model;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * 信任资料快照：吊销批次与公钥档案（按代次拉取，供 Verifier TrustStore 更新）。
 *
 * <p>仅此调用允许 last-known-good：获取失败沿用最近有效快照并标记滞后；
 * 摘要校验失败不安装。</p>
 *
 * @author surezzzzzz
 */
public final class TrustMaterialSnapshot {

    private final long sequence;
    private final long issuedAt;
    private final String snapshotId;
    private final String manifestJws;
    private final List<String> revocationPartJws;
    private final List<String> trustedPublicKeysJson;

    public TrustMaterialSnapshot(long sequence, long issuedAt, String snapshotId,
                                 List<String> revocationPartJws, List<String> trustedPublicKeysJson) {
        this(sequence, issuedAt, snapshotId, null, revocationPartJws, trustedPublicKeysJson);
    }

    /** 完整吊销包快照：manifest 与分片必须作为同一批原始 JWS 交给 Verifier 验证。 */
    public TrustMaterialSnapshot(long sequence, long issuedAt, String snapshotId, String manifestJws,
                                 List<String> revocationPartJws, List<String> trustedPublicKeysJson) {
        this.sequence = sequence;
        this.issuedAt = issuedAt;
        this.snapshotId = snapshotId;
        this.manifestJws = manifestJws;
        this.revocationPartJws = revocationPartJws == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(revocationPartJws));
        this.trustedPublicKeysJson = trustedPublicKeysJson == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(trustedPublicKeysJson));
    }

    public long getSequence() {
        return sequence;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getManifestJws() {
        return manifestJws;
    }

    /** 吊销分片原始 Compact JWS 列表（验签与摘要由 Verifier 侧执行）。 */
    public List<String> getRevocationPartJws() {
        return revocationPartJws;
    }

    /** 公钥信任资料 JSON 列表。 */
    public List<String> getTrustedPublicKeysJson() {
        return trustedPublicKeysJson;
    }
}
