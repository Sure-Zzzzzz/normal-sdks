package io.github.surezzzzzz.sdk.license.core.codec;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 吊销批次结构：manifest（分片摘要清单）+ 分片条目集合（协议 2 §7）。
 *
 * <p>同批次 protocolVersion/scope/sequence/issuedAt/snapshotId 一致；分片按 partIndex 从 0 连续；
 * 每片最多 4096 条、单批 1..256 片；条目按类型/编号唯一；空名单也恰一片空 entries。
 * 接收端全部通过后原子合并，sequence 不高于已安装版本不替换。</p>
 *
 * @author surezzzzzz
 */
public final class RevocationBatch {

    private final String issuerId;
    private final String productId;
    private final long sequence;
    private final long issuedAt;
    private final String snapshotId;
    private final List<List<RevocationEntry>> parts;

    private RevocationBatch(String issuerId, String productId, long sequence, long issuedAt,
                            String snapshotId, List<List<RevocationEntry>> parts) {
        Protocol2Credential.validateId(issuerId, "issuerId");
        Protocol2Credential.validateId(productId, "productId");
        Protocol2Credential.validateId(snapshotId, "snapshotId");
        if (sequence < 1 || sequence > ProtocolValueHelper.SAFE_INTEGER_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        Protocol2Credential.validateEpoch(issuedAt, "issuedAt");
        if (parts == null || parts.isEmpty() || parts.size() > SmartLicenseCoreConstant.REVOCATION_PARTS_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        Set<String> seen = new HashSet<String>();
        boolean anyEmptyPart = false;
        for (List<RevocationEntry> part : parts) {
            if (part == null
                    || part.size() > SmartLicenseCoreConstant.REVOCATION_ENTRIES_PER_PART_MAX) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            // 空名单快照恰一片空 entries；非空批次各片都必须非空
            if (part.isEmpty()) {
                if (parts.size() != 1) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
                anyEmptyPart = true;
                continue;
            }
            for (RevocationEntry entry : part) {
                if (!seen.add(entry.deduplicationKey())) {
                    throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
        }
        if (anyEmptyPart && !seen.isEmpty()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        this.issuerId = issuerId;
        this.productId = productId;
        this.sequence = sequence;
        this.issuedAt = issuedAt;
        this.snapshotId = snapshotId;
        List<List<RevocationEntry>> copied = new ArrayList<List<RevocationEntry>>(parts.size());
        for (List<RevocationEntry> part : parts) {
            copied.add(new ArrayList<RevocationEntry>(part));
        }
        this.parts = copied;
    }

    /** 组装批次（分片已按 partIndex 有序）。 */
    public static RevocationBatch of(String issuerId, String productId, long sequence, long issuedAt,
                                     String snapshotId, List<List<RevocationEntry>> parts) {
        return new RevocationBatch(issuerId, productId, sequence, issuedAt, snapshotId, parts);
    }

    public String getIssuerId() {
        return issuerId;
    }

    public String getProductId() {
        return productId;
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

    public List<List<RevocationEntry>> getParts() {
        List<List<RevocationEntry>> copied = new ArrayList<List<RevocationEntry>>(parts.size());
        for (List<RevocationEntry> part : parts) {
            copied.add(new ArrayList<RevocationEntry>(part));
        }
        return copied;
    }

    /** 是否应替换已安装版本（sequence 严格更高）。 */
    public boolean supersedes(long installedSequence) {
        return sequence > installedSequence;
    }
}
