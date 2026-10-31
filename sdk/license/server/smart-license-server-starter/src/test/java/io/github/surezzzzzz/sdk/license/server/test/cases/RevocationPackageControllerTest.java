package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseRevocationController;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity;
import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackagePartEntity;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.RevocationPackageService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Arrays;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 吊销包控制器响应契约：元数据与独立交付内容隔离，knownSequence 非负且保持完整快照。
 *
 * <p>消费已验证主体夹具直接调用控制器，不替代真实 HTTP 三权链和 IAM 验收。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class RevocationPackageControllerTest {

    private final RevocationPackageService packages = mock(RevocationPackageService.class);
    private final LicenseRevocationController controller = new LicenseRevocationController(
            mock(RevocationRepository.class), mock(LicenseRevocationGuard.class), packages);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAndGetMetadataExposeSnapshotButNeverSignedContents() throws Exception {
        RevocationPackageEntity entity = readyPackage();
        when(packages.create(anyString(), anyString())).thenReturn(entity);
        when(packages.get(entity.getPackageId())).thenReturn(entity);

        ResponseEntity<String> created = controller.createPackage("product-1", "client-key",
                LicenseIdentityHttpTest.TestRequests.authenticated());
        ResponseEntity<String> queried = controller.getPackage(entity.getPackageId(),
                LicenseIdentityHttpTest.TestRequests.authenticated());

        assertThat(created.getStatusCodeValue()).isEqualTo(201);
        assertThat(queried.getStatusCodeValue()).isEqualTo(200);
        for (ResponseEntity<String> response : Arrays.asList(created, queried)) {
            JsonNode metadata = StrictJson.mapper().readTree(response.getBody());
            assertThat(metadata.path("snapshotId").asText()).isEqualTo("snapshot-1");
            assertThat(metadata.path("packageId").asText()).isEqualTo(entity.getPackageId());
            assertThat(metadata.path("state").asText()).isEqualTo("READY");
            assertThat(metadata.has("manifestJws")).isFalse();
            assertThat(metadata.has("revocationPartJws")).isFalse();
            assertThat(metadata.path("resultResource").asText())
                    .isEqualTo("/api/license/v1/revocation-packages/package-1/content");
        }
    }

    @Test
    void contentReturnsExactManifestAndPartsWithNoStore() throws Exception {
        RevocationPackageEntity entity = readyPackage();
        when(packages.get(entity.getPackageId())).thenReturn(entity);
        when(packages.content(entity.getPackageId())).thenReturn(Collections.singletonList(
                new RevocationPackagePartEntity(entity.getPackageId(), 0, "part.raw.signature", "part-digest", 0)));

        ResponseEntity<String> response = controller.packageContent(entity.getPackageId(),
                LicenseIdentityHttpTest.TestRequests.authenticated());

        JsonNode body = StrictJson.mapper().readTree(response.getBody());
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(body.path("manifestJws").asText()).isEqualTo("manifest.raw.signature");
        assertThat(body.path("revocationPartJws")).hasSize(1);
        assertThat(body.path("revocationPartJws").get(0).asText()).isEqualTo("part.raw.signature");
        assertThat(body.path("snapshotId").asText()).isEqualTo(entity.getSnapshotId());
    }

    @Test
    void negativeKnownSequenceIsRejectedBeforeFetchingTrustMaterial() {
        assertThatThrownBy(() -> controller.trustMaterial("product-1", -1L,
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(400);
        verify(packages, never()).trustMaterial(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void trustMaterialPreservesTheFullSnapshotAndAddsNoStore() {
        ObjectNode full = StrictJson.mapper().createObjectNode();
        full.put("sequence", 3L);
        full.put("manifestJws", "manifest.raw.signature");
        full.putArray("revocationPartJws").add("part.raw.signature");
        full.putArray("trustedPublicKeysJson").add("{\"algorithm\":\"ES256\"}");
        when(packages.trustMaterial("product-1", 3L)).thenReturn(full);

        ResponseEntity<String> response = controller.trustMaterial("product-1", 3L,
                LicenseIdentityHttpTest.TestRequests.authenticated());

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo(full.toString());
        verify(packages).trustMaterial("product-1", 3L);
    }

    private static RevocationPackageEntity readyPackage() {
        RevocationPackageEntity entity = new RevocationPackageEntity("package-1", "operation-1", "issuer-1",
                "product-1", 1L, "snapshot-1", "profile-1", "kid-1", 1, "request-digest");
        entity.markReady("manifest.raw.signature", 1, "manifest-digest");
        return entity;
    }
}
