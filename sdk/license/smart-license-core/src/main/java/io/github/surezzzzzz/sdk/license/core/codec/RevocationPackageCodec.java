package io.github.surezzzzzz.sdk.license.core.codec;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协议 2 吊销包的严格编解码与整批验签器。
 *
 * <p>此类只接受原始 Compact JWS；不把已解析对象重新序列化后再验签。
 * 验证顺序固定为：格式、用途/范围、签名、分片摘要、连续性、累计唯一性，全部通过后才返回包句柄。</p>
 *
 * @author surezzzzzz
 */
public final class RevocationPackageCodec {

    private static final String FIELD_PROTOCOL_VERSION = "protocolVersion";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_ISSUER_ID = "issuerId";
    private static final String FIELD_PRODUCT_ID = "productId";
    private static final String FIELD_SEQUENCE = "sequence";
    private static final String FIELD_ISSUED_AT = "issuedAt";
    private static final String FIELD_SNAPSHOT_ID = "snapshotId";
    private static final String FIELD_PART_COUNT = "partCount";
    private static final String FIELD_PART_DIGESTS = "partDigests";
    private static final String FIELD_PART_INDEX = "partIndex";
    private static final String FIELD_ENTRIES = "entries";
    private static final String FIELD_TARGET_KIND = "targetKind";
    private static final String FIELD_TARGET_ID = "targetId";
    private static final String FIELD_REVOKED_AT = "revokedAt";

    private RevocationPackageCodec() {
    }

    /** 解析并验证一整批吊销资料；输入列表只在验证期间使用，不被结果长期持有。 */
    public static VerifiedPackage verify(String manifestJws, List<String> partJws,
                                         String issuerId, String productId, TrustStore trustStore,
                                         long nowEpochSeconds) {
        if (trustStore == null || issuerId == null || productId == null || partJws == null
                || partJws.isEmpty() || partJws.size() > io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.REVOCATION_PARTS_MAX) {
            throw invalid();
        }
        Parsed manifest = parseAndVerify(manifestJws, issuerId, productId, trustStore,
                ProtocolHeader.TYP_REVOCATION_MANIFEST, nowEpochSeconds);
        JsonNode manifestNode = manifest.payload;
        requireFields(manifestNode, FIELD_PROTOCOL_VERSION, FIELD_SCOPE, FIELD_SEQUENCE, FIELD_ISSUED_AT,
                FIELD_SNAPSHOT_ID, FIELD_PART_COUNT, FIELD_PART_DIGESTS);
        int version = integer(manifestNode, FIELD_PROTOCOL_VERSION);
        if (version != io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.PROTOCOL_VERSION) {
            throw invalid();
        }
        Scope scope = scope(manifestNode.get(FIELD_SCOPE), issuerId, productId);
        long sequence = positiveLong(manifestNode, FIELD_SEQUENCE);
        long issuedAt = epoch(manifestNode, FIELD_ISSUED_AT);
        String snapshotId = text(manifestNode, FIELD_SNAPSHOT_ID);
        int partCount = integer(manifestNode, FIELD_PART_COUNT);
        if (partCount < 1 || partCount > io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.REVOCATION_PARTS_MAX
                || !manifestNode.get(FIELD_PART_DIGESTS).isArray()
                || manifestNode.get(FIELD_PART_DIGESTS).size() != partCount
                || issuedAt > nowEpochSeconds + io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.CLOCK_POLICY_SECONDS_MAX) {
            throw invalid();
        }
        List<String> digests = new ArrayList<String>();
        for (JsonNode digest : manifestNode.get(FIELD_PART_DIGESTS)) {
            if (!digest.isTextual() || !digest.asText().matches("[0-9a-f]{64}")) {
                throw invalid();
            }
            digests.add(digest.asText());
        }
        if (partCount != partJws.size()) {
            throw invalid();
        }
        Map<Integer, Part> parts = new HashMap<Integer, Part>();
        Set<String> entries = new HashSet<String>();
        List<RevocationEntry> allEntries = new ArrayList<RevocationEntry>();
        for (int i = 0; i < partJws.size(); i++) {
            String raw = partJws.get(i);
            Parsed parsed = parseAndVerify(raw, issuerId, productId, trustStore,
                    ProtocolHeader.TYP_REVOCATION, nowEpochSeconds);
            if (!manifest.kid.equals(parsed.kid) || !sameCommon(parsed.payload, scope, sequence, issuedAt, snapshotId)) {
                throw invalid();
            }
            String digest;
            try {
                digest = hex(MessageDigest.getInstance("SHA-256"), raw.getBytes(StandardCharsets.UTF_8));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("JRE 缺少 SHA-256", impossible);
            }
            if (!digests.get(i).equals(digest)) {
                throw invalid();
            }
            Part part = parsePart(parsed.payload, scope, sequence, issuedAt, snapshotId);
            // 清单摘要的位置必须与分片已签名索引一致，禁止重排后重新签清单掩盖索引错位。
            if (part.partIndex != i || parts.put(part.partIndex, part) != null
                    || part.entries.isEmpty() && partCount != 1) {
                throw invalid();
            }
            for (RevocationEntry entry : part.entries) {
                if (!entries.add(entry.deduplicationKey())) {
                    throw invalid();
                }
                allEntries.add(entry);
            }
        }
        for (int i = 0; i < partCount; i++) {
            if (!parts.containsKey(i)) {
                throw invalid();
            }
        }
        Collections.sort(allEntries, (left, right) -> left.deduplicationKey().compareTo(right.deduplicationKey()));
        return new VerifiedPackage(scope, sequence, issuedAt, snapshotId, allEntries);
    }

    private static Parsed parseAndVerify(String compact, String issuerId, String productId,
                                         TrustStore trustStore, String expectedTyp, long now) {
        CompactJws jws = CompactJws.parse(compact);
        byte[] headerBytes = Base64Url.decode(jws.getHeaderSegment());
        if (headerBytes.length > io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.HEADER_MAX_BYTES) {
            throw invalid();
        }
        JsonNode header = decode(headerBytes);
        requireFields(header, "alg", "kid", "typ");
        if (!"ES256".equals(text(header, "alg")) || !expectedTyp.equals(text(header, "typ"))) {
            throw invalid();
        }
        String kid = text(header, "kid");
        TrustedPublicKey key = trustStore.findKey(issuerId, productId, kid,
                TrustedPublicKey.Purpose.REVOCATION).orElseThrow(RevocationPackageCodec::invalid);
        if (!Es256Primitives.verify(jws.signingInput(), jws.signatureBytes(),
                Es256Primitives.parseSpkiPublicKey(key.getSpki()))) {
            throw invalid();
        }
        byte[] payloadBytes = Base64Url.decode(jws.getPayloadSegment());
        if (payloadBytes.length > io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES) {
            throw invalid();
        }
        JsonNode payload = decode(payloadBytes);
        return new Parsed(header, payload, kid);
    }

    private static Part parsePart(JsonNode node, Scope scope, long sequence, long issuedAt, String snapshotId) {
        requireFields(node, FIELD_PROTOCOL_VERSION, FIELD_SCOPE, FIELD_SEQUENCE, FIELD_ISSUED_AT,
                FIELD_SNAPSHOT_ID, FIELD_PART_INDEX, FIELD_ENTRIES);
        if (integer(node, FIELD_PROTOCOL_VERSION) != 2 || !sameCommon(node, scope, sequence, issuedAt, snapshotId)
                || !node.get(FIELD_ENTRIES).isArray()
                || node.get(FIELD_ENTRIES).size() > io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.REVOCATION_ENTRIES_PER_PART_MAX) {
            throw invalid();
        }
        int index = integer(node, FIELD_PART_INDEX);
        List<RevocationEntry> entries = new ArrayList<RevocationEntry>();
        for (JsonNode item : node.get(FIELD_ENTRIES)) {
            requireFields(item, FIELD_TARGET_KIND, FIELD_TARGET_ID, FIELD_REVOKED_AT);
            RevocationEntry.TargetKind kind = RevocationEntry.TargetKind.valueOf(text(item, FIELD_TARGET_KIND));
            entries.add(RevocationEntry.of(kind, text(item, FIELD_TARGET_ID), epoch(item, FIELD_REVOKED_AT)));
        }
        return new Part(index, entries);
    }

    private static boolean sameCommon(JsonNode node, Scope scope, long sequence, long issuedAt, String snapshotId) {
        JsonNode scopeNode = node == null ? null : node.get(FIELD_SCOPE);
        return scopeNode != null && scopeNode.isObject() && scopeNode.size() == 2
                && scope.issuerId.equals(textNode(scopeNode, FIELD_ISSUER_ID))
                && scope.productId.equals(textNode(scopeNode, FIELD_PRODUCT_ID))
                && sequence == longNode(node.get(FIELD_SEQUENCE))
                && issuedAt == longNode(node.get(FIELD_ISSUED_AT))
                && snapshotId.equals(textNode(node, FIELD_SNAPSHOT_ID));
    }

    private static Scope scope(JsonNode node, String issuerId, String productId) {
        if (node == null || !node.isObject() || node.size() != 2
                || !issuerId.equals(textNode(node, FIELD_ISSUER_ID))
                || !productId.equals(textNode(node, FIELD_PRODUCT_ID))) {
            throw invalid();
        }
        return new Scope(issuerId, productId);
    }

    private static JsonNode decode(byte[] bytes) {
        try {
            return StrictJson.mapper().readTree(bytes);
        } catch (java.io.IOException failure) {
            throw invalid();
        }
    }

    private static void requireFields(JsonNode node, String... names) {
        if (node == null || !node.isObject() || node.size() != names.length) {
            throw invalid();
        }
        for (String name : names) {
            if (!node.has(name)) {
                throw invalid();
            }
        }
    }

    private static String text(JsonNode node, String field) {
        return textNode(node, field);
    }

    private static String textNode(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.has(field) || !node.get(field).isTextual()
                || node.get(field).asText().isEmpty()) {
            throw invalid();
        }
        String value = node.get(field).asText();
        Protocol2Credential.validateId(value, field);
        return value;
    }

    private static int integer(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).canConvertToInt() || !node.get(field).isIntegralNumber()) {
            throw invalid();
        }
        return node.get(field).intValue();
    }

    private static long positiveLong(JsonNode node, String field) {
        // 代次是正安全整数，不是日期；不可误用 epoch 的较小上限。
        long value = longNode(node == null ? null : node.get(field));
        return value < 1 || value > ProtocolValueHelper.SAFE_INTEGER_MAX ? invalidLong() : value;
    }

    private static long epoch(JsonNode node, String field) {
        long value = longNode(node == null ? null : node.get(field));
        if (value < 0 || value > io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant.EPOCH_SECONDS_MAX) {
            throw invalid();
        }
        return value;
    }

    private static long longNode(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid();
        }
        return node.longValue();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("吊销包协议校验失败");
    }

    private static long invalidLong() {
        throw invalid();
    }

    private static String hex(MessageDigest digest, byte[] bytes) {
        digest.update(bytes);
        byte[] value = digest.digest();
        StringBuilder result = new StringBuilder(64);
        for (byte item : value) {
            result.append(String.format("%02x", item & 255));
        }
        return result.toString();
    }

    private static final class Parsed {
        private final JsonNode header;
        private final JsonNode payload;
        private final String kid;
        private Parsed(JsonNode header, JsonNode payload, String kid) {
            this.header = header;
            this.payload = payload;
            this.kid = kid;
        }
    }

    private static final class Part {
        private final int partIndex;
        private final List<RevocationEntry> entries;
        private Part(int partIndex, List<RevocationEntry> entries) {
            this.partIndex = partIndex;
            this.entries = entries;
        }
    }

    /** 已完整验证的不可变批次；调用方可将其交给原子安装端口。 */
    public static final class VerifiedPackage {
        private final Scope scope;
        private final long sequence;
        private final long issuedAt;
        private final String snapshotId;
        private final List<RevocationEntry> entries;
        private VerifiedPackage(Scope scope, long sequence, long issuedAt, String snapshotId,
                                List<RevocationEntry> entries) {
            this.scope = scope;
            this.sequence = sequence;
            this.issuedAt = issuedAt;
            this.snapshotId = snapshotId;
            this.entries = Collections.unmodifiableList(new ArrayList<RevocationEntry>(entries));
        }
        public String getIssuerId() { return scope.issuerId; }
        public String getProductId() { return scope.productId; }
        public long getSequence() { return sequence; }
        public long getIssuedAt() { return issuedAt; }
        public String getSnapshotId() { return snapshotId; }
        public List<RevocationEntry> getEntries() { return entries; }
    }

    private static final class Scope {
        private final String issuerId;
        private final String productId;
        private Scope(String issuerId, String productId) {
            this.issuerId = issuerId;
            this.productId = productId;
        }
    }
}
