package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.ProtocolHeader;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationEntry;
import io.github.surezzzzzz.sdk.license.core.codec.RevocationPackageCodec;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.model.TrustedPublicKey;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * 用真实 ES256 签名核验吊销分片关联，负例不会因无效签名提前失败。
 *
 * @author surezzzzzz
 */
@Slf4j
class RevocationPackageCodecTest {

    private final KeyPair keys = keys();

    private KeyPair keys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private TrustStore trust() {
        final TrustedPublicKey key = TrustedPublicKey.of("issuer", "product", "key",
                TrustedPublicKey.Purpose.REVOCATION, 1, keys.getPublic().getEncoded());
        return new TrustStore() {
            @Override
            public Optional<TrustedPublicKey> findKey(String issuer, String product, String kid,
                                                      TrustedPublicKey.Purpose purpose) {
                return "issuer".equals(issuer) && "product".equals(product) && "key".equals(kid)
                        && purpose == TrustedPublicKey.Purpose.REVOCATION ? Optional.of(key) : Optional.empty();
            }

            @Override
            public void install(TrustedPublicKey value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long currentTrustVersion(String issuer, String product, TrustedPublicKey.Purpose purpose) {
                return 1;
            }
        };
    }

    private ObjectNode common(long sequence, String snapshot) {
        ObjectNode payload = StrictJson.mapper().createObjectNode();
        payload.put("protocolVersion", 2);
        payload.putObject("scope").put("issuerId", "issuer").put("productId", "product");
        payload.put("sequence", sequence).put("issuedAt", 100).put("snapshotId", snapshot);
        return payload;
    }

    private String part(int index, long sequence, String snapshot, boolean empty) throws Exception {
        ObjectNode payload = common(sequence, snapshot);
        payload.put("partIndex", index);
        ArrayNode entries = payload.putArray("entries");
        if (!empty) {
            entries.addObject().put("targetKind", "GRANT").put("targetId", "grant-" + index).put("revokedAt", 99);
        }
        return sign(payload, ProtocolHeader.TYP_REVOCATION);
    }

    private String manifest(List<String> parts, long sequence, String snapshot) throws Exception {
        ObjectNode payload = common(sequence, snapshot);
        payload.put("partCount", parts.size());
        ArrayNode digests = payload.putArray("partDigests");
        for (String raw : parts) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 255));
            }
            digests.add(hex.toString());
        }
        return sign(payload, ProtocolHeader.TYP_REVOCATION_MANIFEST);
    }

    private String sign(ObjectNode payload, String typ) throws Exception {
        ObjectNode header = StrictJson.mapper().createObjectNode();
        header.put("alg", "ES256").put("kid", "key").put("typ", typ);
        String input = Base64Url.encode(StrictJson.mapper().writeValueAsBytes(header)) + "."
                + Base64Url.encode(StrictJson.mapper().writeValueAsBytes(payload));
        return input + "." + Base64Url.encode(Es256Primitives.signForTesting(
                input.getBytes(StandardCharsets.US_ASCII), keys.getPrivate()));
    }

    private RevocationPackageCodec.VerifiedPackage verify(List<String> parts, long sequence, String snapshot)
            throws Exception {
        return RevocationPackageCodec.verify(manifest(parts, sequence, snapshot), parts,
                "issuer", "product", trust(), 100);
    }

    @Test
    void orderedPartsAcceptButResignedReorderingRejects() throws Exception {
        String first = part(0, 1, "snapshot", false);
        String second = part(1, 1, "snapshot", false);
        log.info("验证有效分片顺序通过，重排后重签清单仍拒绝");
        assertThat(verify(Arrays.asList(first, second), 1, "snapshot").getEntries()).hasSize(2);
        assertThatThrownBy(() -> verify(Arrays.asList(second, first), 1, "snapshot"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sequenceUsesSafeIntegerBudgetInsteadOfEpochBudget() throws Exception {
        log.info("验证代次允许安全整数上界，超上界明确拒绝");
        long sequence = 9007199254740991L;
        List<String> parts = Collections.singletonList(part(0, sequence, "snapshot", false));
        assertThat(verify(parts, sequence, "snapshot").getSequence()).isEqualTo(sequence);
        long overflow = sequence + 1;
        List<String> invalid = Collections.singletonList(part(0, overflow, "snapshot", false));
        assertThatThrownBy(() -> verify(invalid, overflow, "snapshot")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptySnapshotHasExactlyOnePart() throws Exception {
        log.info("验证空快照单片通过，多片空快照与混合空片拒绝");
        assertThat(verify(Collections.singletonList(part(0, 1, "snapshot", true)), 1, "snapshot").getEntries())
                .isEmpty();
        List<String> empty = Arrays.asList(part(0, 1, "snapshot", true), part(1, 1, "snapshot", true));
        assertThatThrownBy(() -> verify(empty, 1, "snapshot")).isInstanceOf(IllegalArgumentException.class);
        List<String> mixed = Arrays.asList(part(0, 1, "snapshot", false), part(1, 1, "snapshot", true));
        assertThatThrownBy(() -> verify(mixed, 1, "snapshot")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedIdentifiersRespectUtf8BudgetAndRejectControlCharacters() throws Exception {
        String permitted = String.join("", Collections.nCopies(42, "界"));
        String overflow = permitted + "界";
        log.info("验证编号按UTF-8字节预算且控制字符拒绝，不输出原编号");
        assertThat(verify(Collections.singletonList(part(0, 1, permitted, false)), 1, permitted).getSequence())
                .isEqualTo(1);
        List<String> tooLong = Collections.singletonList(part(0, 1, overflow, false));
        assertThatThrownBy(() -> verify(tooLong, 1, overflow)).isInstanceOf(RuntimeException.class);
        String control = "snapshot" + (char) 0;
        List<String> invalid = Collections.singletonList(part(0, 1, control, false));
        assertThatThrownBy(() -> verify(invalid, 1, control)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> RevocationEntry.of(RevocationEntry.TargetKind.GRANT, "a" + (char) 0 + "b", 100))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> SubjectRef.of(SubjectKind.SERVICE, "source" + (char) 0, "subject"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubjectRef.of(SubjectKind.PERSON, "source", "subject\n"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubjectBinding.deployment("activation" + (char) 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
