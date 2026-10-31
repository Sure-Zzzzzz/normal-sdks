package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.CompactJws;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.crypto.Es256Primitives;
import io.github.surezzzzzz.sdk.license.core.model.SigningInput;
import io.github.surezzzzzz.sdk.license.core.spi.LicenseSigner;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackagePartEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationScopeEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationPackagePartRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationPackageRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationScopeRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.service.RevocationPackageService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 吊销包协议回归：使用真实 P-256 测试签名核对分片预算、摘要、范围和信任资料响应。
 *
 * <p>仓储为受控替身；数据库事务与并发由独立 MySQL 持久化测试证明。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class RevocationPackageServiceTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final RevocationRepository revocations = mock(RevocationRepository.class);
    private final RevocationScopeRepository scopes = mock(RevocationScopeRepository.class);
    private final RevocationPackageRepository packages = mock(RevocationPackageRepository.class);
    private final RevocationPackagePartRepository parts = mock(RevocationPackagePartRepository.class);
    private final SigningProfileRepository profiles = mock(SigningProfileRepository.class);
    private final LicenseSigner signer = mock(LicenseSigner.class);
    private final Map<String, RevocationPackageEntity> saved = new HashMap<>();
    private final Map<String, List<RevocationPackagePartEntity>> savedParts = new HashMap<>();
    private RevocationScopeEntity scope;
    private SigningProfileEntity profile;
    private KeyPair keyPair;
    private RevocationPackageService service;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        profile = new SigningProfileEntity("profile-1", "product-1", "REVOCATION", "kid-1",
                "key-1", 3, hex(keyPair.getPublic().getEncoded()));
        when(products.lockByProductId("product-1")).thenReturn(Optional.of(product()));
        when(products.findById("product-1")).thenReturn(Optional.of(product()));
        when(profiles.findByProductIdAndPurposeAndStatus("product-1", "REVOCATION", "ACTIVE"))
                .thenReturn(Optional.of(profile));
        when(profiles.findByProductIdAndPurpose("product-1", "REVOCATION"))
                .thenReturn(Collections.singletonList(profile));
        when(scopes.lockByIssuerIdAndProductId("issuer-1", "product-1"))
                .thenAnswer(invocation -> Optional.ofNullable(scope));
        when(scopes.findByIssuerIdAndProductId("issuer-1", "product-1"))
                .thenAnswer(invocation -> Optional.ofNullable(scope));
        when(scopes.saveAndFlush(any(RevocationScopeEntity.class))).thenAnswer(invocation -> {
            scope = invocation.getArgument(0);
            return scope;
        });
        when(packages.saveAndFlush(any(RevocationPackageEntity.class))).thenAnswer(invocation -> {
            RevocationPackageEntity entity = invocation.getArgument(0);
            saved.put(entity.getPackageId(), entity);
            return entity;
        });
        when(packages.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(saved.get(invocation.getArgument(0))));
        when(packages.findByIdempotencyDigest(anyString())).thenAnswer(invocation -> saved.values().stream()
                .filter(entity -> entity.getIdempotencyDigest().equals(invocation.getArgument(0))).findFirst());
        when(packages.findTopByProductIdAndStateOrderBySequenceDesc("product-1", "READY"))
                .thenAnswer(invocation -> saved.values().stream().filter(entity -> "READY".equals(entity.getState()))
                        .max(java.util.Comparator.comparingLong(RevocationPackageEntity::getSequence)));
        when(parts.save(any(RevocationPackagePartEntity.class))).thenAnswer(invocation -> {
            RevocationPackagePartEntity part = invocation.getArgument(0);
            savedParts.computeIfAbsent(part.getPackageId(), ignored -> new ArrayList<>()).add(part);
            return part;
        });
        when(parts.findByPackageIdOrderByPartIndexAsc(anyString())).thenAnswer(invocation ->
                savedParts.getOrDefault(invocation.getArgument(0), Collections.emptyList()));
        when(revocations.findForProduct("product-1")).thenReturn(Collections.emptyList());
        when(signer.sign(anyString(), anyString(), any(SigningInput.class))).thenAnswer(invocation ->
                Es256Primitives.signForTesting(((SigningInput) invocation.getArgument(2)).getBytes(),
                        keyPair.getPrivate()));
        service = new RevocationPackageService(products, revocations, scopes, packages, parts, profiles, signer);
    }

    @Test
    void emptySnapshotPublishesOneSignedEmptyPartAndSignedManifest() throws Exception {
        RevocationPackageEntity entity = service.create("product-1", "request-1");

        log.info("空吊销名单发布一个有效空分片");
        assertThat(entity.getState()).isEqualTo("READY");
        assertThat(entity.getSequence()).isEqualTo(1L);
        assertThat(entity.getPartCount()).isEqualTo(1);
        List<RevocationPackagePartEntity> content = service.content(entity.getPackageId());
        assertThat(payload(content.get(0).getPartJws()).path("entries")).isEmpty();
        assertSignedSnapshot(entity, content);
        assertThat(scope.getNextSequence()).isEqualTo(1L);
        assertThat(scope.getTrustVersion()).isEqualTo(1L);
        assertThat(scope.getInstalledSequence()).isEqualTo(1L);
    }

    @Test
    void entryLimitSplits4097EntriesWithoutLossOrDuplication() throws Exception {
        when(revocations.findForProduct("product-1")).thenReturn(entries(4097, false));

        RevocationPackageEntity entity = service.create("product-1", "entry-boundary");
        List<RevocationPackagePartEntity> content = service.content(entity.getPackageId());

        log.info("4097条吊销记录按4096条单片上限拆分");
        assertThat(content).hasSize(2);
        assertThat(content.get(0).getEntryCount()).isEqualTo(4096);
        assertThat(content.get(1).getEntryCount()).isEqualTo(1);
        assertAllEntriesOnce(content, 4097);
        assertSignedSnapshot(entity, content);
    }

    @Test
    void byteBudgetSplitsBeforeTheEntryLimit() throws Exception {
        when(revocations.findForProduct("product-1")).thenReturn(entries(3000, true));

        RevocationPackageEntity entity = service.create("product-1", "byte-boundary");
        List<RevocationPackagePartEntity> content = service.content(entity.getPackageId());

        log.info("长编号吊销记录按解码后512KiB预算拆分");
        assertThat(content).hasSizeGreaterThan(1);
        assertThat(content.get(0).getEntryCount()).isLessThan(4096);
        assertAllEntriesOnce(content, 3000);
        assertSignedSnapshot(entity, content);
    }

    @Test
    void allFiveTargetKindsArePreservedInTheSignedSnapshot() throws Exception {
        List<RevocationEntity> source = new ArrayList<>();
        for (String kind : Arrays.asList("GRANT", "CREDENTIAL", "ACTIVATION", "ASSIGNMENT", "SIGNING_KEY")) {
            source.add(new RevocationEntity("rv-" + kind, kind, "target-" + kind, "SECURITY", "actor-example"));
        }
        when(revocations.findForProduct("product-1")).thenReturn(source);

        RevocationPackageEntity entity = service.create("product-1", "five-kinds");
        JsonNode values = payload(service.content(entity.getPackageId()).get(0).getPartJws()).path("entries");

        assertThat(values).extracting(value -> value.path("targetKind").asText())
                .containsExactly("GRANT", "CREDENTIAL", "ACTIVATION", "ASSIGNMENT", "SIGNING_KEY");
        for (JsonNode value : values) {
            assertThat(value.path("revokedAt").isIntegralNumber()).isTrue();
        }
    }

    @Test
    void oversizedSingleEntryIsRejectedBeforeAnySignatureInsteadOfPublishingAnInvalidPart() {
        String oversizedId = String.join("", Collections.nCopies(SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES, "x"));
        when(revocations.findForProduct("product-1")).thenReturn(Collections.singletonList(
                new RevocationEntity("rv-too-large", "GRANT", oversizedId, "SECURITY", "actor-example")));

        assertThatThrownBy(() -> service.create("product-1", "entry-too-large"))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        verify(signer, never()).sign(anyString(), anyString(), any(SigningInput.class));
        RevocationPackageEntity failed = saved.values().iterator().next();
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(failed.getManifestJws()).isNull();
        assertThat(scope.getNextSequence()).isEqualTo(1L);
        assertThat(scope.getInstalledSequence()).isZero();
    }

    @Test
    void idempotentReplayDoesNotAllocateAnotherSequenceOrSignAgain() {
        RevocationPackageEntity first = service.create("product-1", "repeat-request");

        assertThat(service.create("product-1", "repeat-request")).isSameAs(first);
        assertThat(scope.getNextSequence()).isEqualTo(1L);
        assertThat(scope.getTrustVersion()).isEqualTo(1L);
        assertThat(saved).hasSize(1);
        verify(signer, times(2)).sign(anyString(), anyString(), any(SigningInput.class));
    }

    @Test
    void failedSignatureKeepsItsWatermarksAndFailedReplayDoesNotSignAgain() {
        when(signer.sign(anyString(), anyString(), any(SigningInput.class)))
                .thenThrow(new IllegalStateException("模拟签名依赖失败"));

        assertThatThrownBy(() -> service.create("product-1", "failed-request"))
                .isInstanceOf(IllegalStateException.class);
        RevocationPackageEntity failed = saved.values().iterator().next();
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(service.create("product-1", "failed-request")).isSameAs(failed);
        assertThat(scope.getNextSequence()).isEqualTo(1L);
        assertThat(scope.getTrustVersion()).isEqualTo(1L);
        verify(signer, times(1)).sign(anyString(), anyString(), any(SigningInput.class));
        assertThat(scope.getInstalledSequence()).isZero();
        assertThatThrownBy(() -> service.content(failed.getPackageId())).isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void wrongSignatureShapeFailsWithoutPublishingContent() {
        when(signer.sign(anyString(), anyString(), any(SigningInput.class))).thenReturn(new byte[63]);

        assertThatThrownBy(() -> service.create("product-1", "bad-signature"))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
        assertThat(saved.values()).allSatisfy(entity -> {
            assertThat(entity.getState()).isEqualTo("FAILED");
            assertThat(entity.getManifestJws()).isNull();
        });
    }

    @Test
    void manifestFailureLeavesThePackageUnavailableDespiteSavedParts() {
        AtomicInteger calls = new AtomicInteger();
        when(signer.sign(anyString(), anyString(), any(SigningInput.class)))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new IllegalStateException("模拟清单签名失败");
                    }
                    return Es256Primitives.signForTesting(((SigningInput) invocation.getArgument(2)).getBytes(),
                            keyPair.getPrivate());
                });

        assertThatThrownBy(() -> service.create("product-1", "manifest-failure"))
                .isInstanceOf(IllegalStateException.class);
        RevocationPackageEntity failed = saved.values().iterator().next();
        assertThat(savedParts.get(failed.getPackageId())).hasSize(1);
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(scope.getInstalledSequence()).isZero();
        assertThatThrownBy(() -> service.content(failed.getPackageId()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
    }

    @Test
    void invalid64ByteSignatureFailsBeforeContentAndDoesNotAdvanceExportedSequence() {
        when(signer.sign(anyString(), anyString(), any(SigningInput.class))).thenReturn(new byte[64]);

        assertLocalVerificationFailure("invalid-64-byte-signature", false);
        verify(signer, times(1)).sign(anyString(), anyString(), any(SigningInput.class));
    }

    @Test
    void partSignedByAnotherKeyCannotBecomeReady() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair anotherKey = generator.generateKeyPair();
        when(signer.sign(anyString(), anyString(), any(SigningInput.class))).thenAnswer(invocation ->
                Es256Primitives.signForTesting(((SigningInput) invocation.getArgument(2)).getBytes(),
                        anotherKey.getPrivate()));

        assertLocalVerificationFailure("another-part-key", false);
        verify(signer, times(1)).sign(anyString(), anyString(), any(SigningInput.class));
    }

    @Test
    void manifestSignedByAnotherKeyLeavesValidPartUnavailable() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair anotherKey = generator.generateKeyPair();
        AtomicInteger calls = new AtomicInteger();
        when(signer.sign(anyString(), anyString(), any(SigningInput.class))).thenAnswer(invocation ->
                Es256Primitives.signForTesting(((SigningInput) invocation.getArgument(2)).getBytes(),
                        calls.incrementAndGet() == 1 ? keyPair.getPrivate() : anotherKey.getPrivate()));

        assertLocalVerificationFailure("another-manifest-key", true);
        verify(signer, times(2)).sign(anyString(), anyString(), any(SigningInput.class));
    }

    /** 本地验签失败须不可交付且不刷新成功水位；生成中的序号仍保留。 */
    private void assertLocalVerificationFailure(String request, boolean savedPart) {
        assertThatThrownBy(() -> service.create("product-1", request))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
        RevocationPackageEntity failed = saved.values().iterator().next();
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(failed.getManifestJws()).isNull();
        assertThat(scope.getInstalledSequence()).isZero();
        assertThat(scope.getNextSequence()).isEqualTo(1L);
        assertThat(scope.getTrustVersion()).isEqualTo(1L);
        assertThat(savedParts.getOrDefault(failed.getPackageId(), Collections.emptyList()))
                .hasSize(savedPart ? 1 : 0);
        assertThatThrownBy(() -> service.content(failed.getPackageId()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
    }

    @Test
    void missingProfileIsRejectedBeforeScopeOrPackageCreation() {
        when(profiles.findByProductIdAndPurposeAndStatus("product-1", "REVOCATION", "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("product-1", "no-profile"))
                .isInstanceOf(LicenseHttpException.class);
        verify(scopes, never()).saveAndFlush(any());
        verify(packages, never()).saveAndFlush(any());
        verify(signer, never()).sign(anyString(), anyString(), any(SigningInput.class));
    }

    @Test
    void trustMaterialReturnsExactStringKeysAndRetainsRetiredProfiles() throws Exception {
        RevocationPackageEntity entity = service.create("product-1", "trust-material");
        SigningProfileEntity retired = new SigningProfileEntity("profile-old", "product-1", "REVOCATION",
                "kid-old", "key-old", 2, profile.getRegisteredPublicKeyHex());
        retired.retire();
        when(profiles.findByProductIdAndPurpose("product-1", "REVOCATION"))
                .thenReturn(Arrays.asList(retired, profile));

        ObjectNode body = service.trustMaterial("product-1", 0L);

        assertThat(body.path("manifestJws").asText()).isEqualTo(entity.getManifestJws());
        assertThat(body.path("revocationPartJws")).hasSize(1);
        assertThat(body.path("trustedPublicKeysJson")).hasSize(2);
        for (JsonNode text : body.path("trustedPublicKeysJson")) {
            assertThat(text.isTextual()).isTrue();
            JsonNode key = StrictJson.mapper().readTree(text.asText());
            assertThat(key.path("algorithm").asText()).isEqualTo("ES256");
            assertThat(key.path("issuerId").asText()).isEqualTo("issuer-1");
            assertThat(key.path("productId").asText()).isEqualTo("product-1");
            assertThat(key.path("purpose").asText()).isEqualTo("REVOCATION");
            assertThat(key.path("trustVersion").asLong()).isEqualTo(1L);
            assertThat(Base64Url.decode(key.path("spkiBase64").asText()))
                    .isEqualTo(keyPair.getPublic().getEncoded());
        }
    }

    @Test
    void knownSequenceIsAHintAndAlwaysReturnsTheLatestFullSnapshot() {
        service.create("product-1", "older");
        RevocationPackageEntity latest = service.create("product-1", "newer");

        ObjectNode full = service.trustMaterial("product-1", 0L);

        assertThat(full.path("sequence").asLong()).isEqualTo(latest.getSequence());
        assertThat(service.trustMaterial("product-1", latest.getSequence())).isEqualTo(full);
        assertThat(service.trustMaterial("product-1", latest.getSequence() + 1L)).isEqualTo(full);
        assertThat(full.path("manifestJws").isTextual()).isTrue();
        assertThat(full.path("revocationPartJws")).isNotEmpty();
    }

    @Test
    void absenceOfReadyPackageFailsTrustMaterialInsteadOfReturningAnEmptySuccess() {
        assertThatThrownBy(() -> service.trustMaterial("product-1", 0L))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(409);
    }

    private void assertSignedSnapshot(RevocationPackageEntity entity,
                                      List<RevocationPackagePartEntity> content) throws Exception {
        CompactJws manifest = CompactJws.parse(entity.getManifestJws());
        assertThat(Es256Primitives.verify(manifest.signingInput(), manifest.signatureBytes(), keyPair.getPublic()))
                .isTrue();
        JsonNode manifestPayload = payload(entity.getManifestJws());
        assertThat(header(entity.getManifestJws()).path("typ").asText()).isEqualTo("license-revocation-manifest+jwt");
        assertThat(manifestPayload.path("partCount").asInt()).isEqualTo(content.size());
        assertThat(entity.getManifestSha256()).isEqualTo(sha256(entity.getManifestJws()));
        for (int index = 0; index < content.size(); index++) {
            RevocationPackagePartEntity part = content.get(index);
            CompactJws compact = CompactJws.parse(part.getPartJws());
            assertThat(Es256Primitives.verify(compact.signingInput(), compact.signatureBytes(), keyPair.getPublic()))
                    .isTrue();
            JsonNode partPayload = payload(part.getPartJws());
            assertThat(header(part.getPartJws()).path("typ").asText()).isEqualTo("license-revocations+jwt");
            assertThat(Base64Url.decode(compact.getPayloadSegment()).length)
                    .isLessThanOrEqualTo(SmartLicenseCoreConstant.PAYLOAD_MAX_BYTES);
            assertThat(partPayload.path("entries").size())
                    .isLessThanOrEqualTo(SmartLicenseCoreConstant.REVOCATION_ENTRIES_PER_PART_MAX);
            assertThat(partPayload.path("scope")).isEqualTo(manifestPayload.path("scope"));
            assertThat(partPayload.path("sequence")).isEqualTo(manifestPayload.path("sequence"));
            assertThat(partPayload.path("issuedAt")).isEqualTo(manifestPayload.path("issuedAt"));
            assertThat(partPayload.path("snapshotId")).isEqualTo(manifestPayload.path("snapshotId"));
            assertThat(partPayload.path("partIndex").asInt()).isEqualTo(index);
            assertThat(part.getPartIndex()).isEqualTo(index);
            assertThat(part.getPartSha256()).isEqualTo(sha256(part.getPartJws()));
            assertThat(manifestPayload.path("partDigests").get(index).asText()).isEqualTo(part.getPartSha256());
        }
        assertThat(manifestPayload.path("scope").path("issuerId").asText()).isEqualTo("issuer-1");
        assertThat(manifestPayload.path("scope").path("productId").asText()).isEqualTo("product-1");
        assertThat(manifestPayload.path("sequence").asLong()).isEqualTo(entity.getSequence());
        assertThat(manifestPayload.path("snapshotId").asText()).isEqualTo(entity.getSnapshotId());
    }

    private static void assertAllEntriesOnce(List<RevocationPackagePartEntity> content, int count) throws Exception {
        List<String> ids = new ArrayList<>();
        for (RevocationPackagePartEntity part : content) {
            for (JsonNode entry : payload(part.getPartJws()).path("entries")) {
                ids.add(entry.path("targetId").asText());
            }
        }
        assertThat(ids).hasSize(count).doesNotHaveDuplicates();
    }

    private static List<RevocationEntity> entries(int count, boolean longIds) {
        List<RevocationEntity> result = new ArrayList<>();
        String padding = String.join("", Collections.nCopies(115, "x"));
        for (int index = 0; index < count; index++) {
            result.add(new RevocationEntity("rv-" + index, "GRANT",
                    (longIds ? padding : "target-") + index, "SECURITY", "actor-example"));
        }
        return result;
    }

    private static ProductEntity product() {
        return new ProductEntity("product-1", "issuer-1", "example", "示例产品", "[]", "[]");
    }

    private static JsonNode payload(String compact) throws Exception {
        return StrictJson.mapper().readTree(Base64Url.decode(CompactJws.parse(compact).getPayloadSegment()));
    }

    private static JsonNode header(String compact) throws Exception {
        return StrictJson.mapper().readTree(Base64Url.decode(CompactJws.parse(compact).getHeaderSegment()));
    }

    private static String sha256(String text) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 255));
        }
        return result.toString();
    }
}
