package io.github.surezzzzzz.sdk.license.verifier.test.cases;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.client.model.TrustMaterialSnapshot;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.ProtocolHeader;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.FileRevocationStore;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.InMemoryTrustStore;
import io.github.surezzzzzz.sdk.license.verifier.service.LicenseTrustMaterialInstaller;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 完整信任资料安装回归：真实 ES256 多分片整包验签，任何拒绝均保留原有持久化吊销仓。
 *
 * <p>直接使用消费侧快照契约和正式 Codec/Installer/FileStore，不替代真实 HTTP 传输验收。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseTrustMaterialInstallerTest {

    @TempDir
    private Path directory;
    private KeyPair keyPair;
    private InMemoryTrustStore trust;
    private FileRevocationStore store;
    private LicenseTrustMaterialInstaller installer;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = keyPair();
        trust = new InMemoryTrustStore();
        trust.install(TrustedPublicKey.of("issuer-1", "product-1", "kid-1",
                TrustedPublicKey.Purpose.REVOCATION, 1L, keyPair.getPublic().getEncoded()));
        store = new FileRevocationStore(directory);
        installer = new LicenseTrustMaterialInstaller(trust, store);
        installer.install("issuer-1", "product-1", snapshot(keyPair, "issuer-1", 1L, "snapshot-old",
                Collections.singletonList(Collections.singletonList("credential-old"))), 2000L);
    }

    @Test
    void completeSignedMultiPartSnapshotInstallsAsOneBatchAndSurvivesReload() throws Exception {
        TrustMaterialSnapshot next = nextSnapshot();

        installer.install("issuer-1", "product-1", next, 2000L);

        log.info("真实 ES256 清单和两个分片完整核验后一起安装");
        FileRevocationStore reloaded = new FileRevocationStore(directory);
        assertThat(reloaded.installedSequence("issuer-1", "product-1")).hasValue(2L);
        assertThat(reloaded.installedIssuedAt("issuer-1", "product-1")).contains(1002L);
        assertThat(reloaded.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-new-1")).isTrue();
        assertThat(reloaded.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-new-2")).isTrue();
        assertThat(reloaded.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-old")).isTrue();
        assertThat(trust.currentTrustVersion("issuer-1", "product-1", TrustedPublicKey.Purpose.REVOCATION))
                .isEqualTo(1L);
    }

    @Test
    void unsignedMetadataConflictsCannotChangeTheInstalledBatch() throws Exception {
        TrustMaterialSnapshot next = nextSnapshot();
        List<TrustMaterialSnapshot> invalid = Arrays.asList(
                copy(next, 3L, next.getIssuedAt(), next.getSnapshotId(), next.getRevocationPartJws()),
                copy(next, next.getSequence(), next.getIssuedAt() + 1, next.getSnapshotId(),
                        next.getRevocationPartJws()),
                copy(next, next.getSequence(), next.getIssuedAt(), "different-snapshot",
                        next.getRevocationPartJws()));

        for (TrustMaterialSnapshot conflict : invalid) {
            assertThatThrownBy(() -> installer.install("issuer-1", "product-1", conflict, 2000L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertOldSnapshot();
        }
    }

    @Test
    void missingOrReorderedPartsRejectTheWholeBatch() throws Exception {
        TrustMaterialSnapshot next = nextSnapshot();
        List<String> reversed = new ArrayList<String>(next.getRevocationPartJws());
        Collections.reverse(reversed);
        List<TrustMaterialSnapshot> invalid = Arrays.asList(
                copy(next, 2L, next.getIssuedAt(), next.getSnapshotId(),
                        Collections.singletonList(next.getRevocationPartJws().get(0))),
                copy(next, 2L, next.getIssuedAt(), next.getSnapshotId(), reversed));

        for (TrustMaterialSnapshot incomplete : invalid) {
            assertThatThrownBy(() -> installer.install("issuer-1", "product-1", incomplete, 2000L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertOldSnapshot();
        }
    }

    @Test
    void signedManifestWithWrongPartDigestDoesNotInstallAnyPart() throws Exception {
        TrustMaterialSnapshot next = nextSnapshot();
        ObjectNode wrongManifest = common("issuer-1", 2L, "snapshot-new");
        wrongManifest.put("partCount", 2);
        wrongManifest.putArray("partDigests")
                .add(String.join("", Collections.nCopies(64, "0")))
                .add(sha256(next.getRevocationPartJws().get(1)));
        TrustMaterialSnapshot wrong = new TrustMaterialSnapshot(2L, next.getIssuedAt(), next.getSnapshotId(),
                sign(keyPair, wrongManifest, ProtocolHeader.TYP_REVOCATION_MANIFEST),
                next.getRevocationPartJws(), next.getTrustedPublicKeysJson());

        assertThatThrownBy(() -> installer.install("issuer-1", "product-1", wrong, 2000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertOldSnapshot();
    }

    @Test
    void changedPartSignatureAndWrongTrustDomainBothKeepTheOldStore() throws Exception {
        TrustMaterialSnapshot next = nextSnapshot();
        List<String> modifiedParts = new ArrayList<String>(next.getRevocationPartJws());
        String part = modifiedParts.get(1);
        int signatureStart = part.lastIndexOf('.') + 1;
        char changed = part.charAt(signatureStart) == 'A' ? 'B' : 'A';
        modifiedParts.set(1, part.substring(0, signatureStart) + changed + part.substring(signatureStart + 1));
        TrustMaterialSnapshot corrupted = copy(next, 2L, next.getIssuedAt(), next.getSnapshotId(), modifiedParts);

        assertThatThrownBy(() -> installer.install("issuer-1", "product-1", corrupted, 2000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertOldSnapshot();
        TrustMaterialSnapshot wrongDomain = snapshot(keyPair, "issuer-2", 2L, "snapshot-new",
                Collections.singletonList(Collections.singletonList("credential-new-1")));
        assertThatThrownBy(() -> installer.install("issuer-1", "product-1", wrongDomain, 2000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertOldSnapshot();
    }

    @Test
    void responsePublicKeysCannotReplaceTheRegisteredTrustRoot() throws Exception {
        KeyPair attacker = keyPair();
        TrustMaterialSnapshot untrusted = snapshot(attacker, "issuer-1", 2L, "snapshot-new",
                Collections.singletonList(Collections.singletonList("credential-new-1")));
        ObjectNode allegedKey = StrictJson.mapper().createObjectNode();
        allegedKey.put("issuerId", "issuer-1").put("productId", "product-1").put("kid", "kid-1");
        allegedKey.put("purpose", "REVOCATION").put("trustVersion", 2L).put("algorithm", "ES256");
        allegedKey.put("spkiBase64", Base64Url.encode(attacker.getPublic().getEncoded()));
        TrustMaterialSnapshot response = new TrustMaterialSnapshot(untrusted.getSequence(), untrusted.getIssuedAt(),
                untrusted.getSnapshotId(), untrusted.getManifestJws(), untrusted.getRevocationPartJws(),
                Collections.singletonList(allegedKey.toString()));

        assertThatThrownBy(() -> installer.install("issuer-1", "product-1", response, 2000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertOldSnapshot();
        assertThat(trust.findKey("issuer-1", "product-1", "kid-1", TrustedPublicKey.Purpose.REVOCATION)
                .get().getSpki()).isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void legacySnapshotWithoutManifestIsExplicitlyRejected() throws Exception {
        TrustMaterialSnapshot next = nextSnapshot();
        TrustMaterialSnapshot legacy = new TrustMaterialSnapshot(next.getSequence(), next.getIssuedAt(),
                next.getSnapshotId(), next.getRevocationPartJws(), next.getTrustedPublicKeysJson());

        assertThatThrownBy(() -> installer.install("issuer-1", "product-1", legacy, 2000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertOldSnapshot();
    }

    private void assertOldSnapshot() {
        FileRevocationStore persisted = new FileRevocationStore(directory);
        for (FileRevocationStore current : Arrays.asList(store, persisted)) {
            assertThat(current.installedSequence("issuer-1", "product-1")).hasValue(1L);
            assertThat(current.installedIssuedAt("issuer-1", "product-1")).contains(1001L);
            assertThat(current.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-old")).isTrue();
            assertThat(current.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-new-1")).isFalse();
            assertThat(current.isRevoked("issuer-1", "product-1", "CREDENTIAL", "credential-new-2")).isFalse();
        }
    }

    private TrustMaterialSnapshot nextSnapshot() throws Exception {
        return snapshot(keyPair, "issuer-1", 2L, "snapshot-new", Arrays.asList(
                Arrays.asList("credential-old", "credential-new-1"), Collections.singletonList("credential-new-2")));
    }

    private static TrustMaterialSnapshot copy(TrustMaterialSnapshot source, long sequence, long issuedAt,
                                              String snapshotId, List<String> parts) {
        return new TrustMaterialSnapshot(sequence, issuedAt, snapshotId, source.getManifestJws(), parts,
                source.getTrustedPublicKeysJson());
    }

    private static TrustMaterialSnapshot snapshot(KeyPair signer, String issuer, long sequence, String snapshotId,
                                                  List<List<String>> entries) throws Exception {
        List<String> parts = new ArrayList<String>();
        ObjectNode manifest = common(issuer, sequence, snapshotId);
        manifest.put("partCount", entries.size());
        com.fasterxml.jackson.databind.node.ArrayNode digests = manifest.putArray("partDigests");
        for (int index = 0; index < entries.size(); index++) {
            ObjectNode part = common(issuer, sequence, snapshotId);
            part.put("partIndex", index);
            com.fasterxml.jackson.databind.node.ArrayNode items = part.putArray("entries");
            for (String target : entries.get(index)) {
                items.addObject().put("targetKind", "CREDENTIAL").put("targetId", target)
                        .put("revokedAt", 1000L);
            }
            String raw = sign(signer, part, ProtocolHeader.TYP_REVOCATION);
            parts.add(raw);
            digests.add(sha256(raw));
        }
        return new TrustMaterialSnapshot(sequence, 1000L + sequence, snapshotId,
                sign(signer, manifest, ProtocolHeader.TYP_REVOCATION_MANIFEST), parts, Collections.emptyList());
    }

    private static ObjectNode common(String issuer, long sequence, String snapshotId) {
        ObjectNode node = StrictJson.mapper().createObjectNode();
        node.put("protocolVersion", 2);
        node.putObject("scope").put("issuerId", issuer).put("productId", "product-1");
        node.put("sequence", sequence).put("issuedAt", 1000L + sequence).put("snapshotId", snapshotId);
        return node;
    }

    private static String sign(KeyPair signer, ObjectNode payload, String typ) throws Exception {
        ObjectNode header = StrictJson.mapper().createObjectNode();
        header.put("alg", "ES256").put("kid", "kid-1").put("typ", typ);
        String input = Base64Url.encode(StrictJson.mapper().writeValueAsBytes(header)) + "."
                + Base64Url.encode(StrictJson.mapper().writeValueAsBytes(payload));
        return input + "." + Base64Url.encode(Es256Primitives.signForTesting(
                input.getBytes(StandardCharsets.US_ASCII), signer.getPrivate()));
    }

    private static String sha256(String raw) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 255));
        }
        return hex.toString();
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
