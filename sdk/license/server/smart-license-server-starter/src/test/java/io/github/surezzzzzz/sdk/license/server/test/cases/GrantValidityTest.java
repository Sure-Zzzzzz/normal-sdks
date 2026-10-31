package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.constant.GrantValidityConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.service.JpaGrantRegistry;
import io.github.surezzzzzz.sdk.license.server.service.OfflineCredentialComposer;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 授权期限固定事实回归：新建一次固化，在线读取与离线重签不滑动，旧缺失事实明确拒绝。
 *
 * @author surezzzzzz
 */
@Slf4j
class GrantValidityTest {

    @Test
    void bothCreationConstructorsFreezeDefaultWindowOnce() {
        long before = Instant.now().getEpochSecond();
        GrantRevisionEntity plain = row();
        GrantRevisionEntity modeled = new GrantRevisionEntity("grant-model", "customer-1", "product-1",
                "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null, "model-1", "{}");
        long after = Instant.now().getEpochSecond();
        for (GrantRevisionEntity created : Arrays.asList(plain, modeled)) {
            assertThat(created.getValidityKind()).isEqualTo("FIXED");
            assertThat(created.getStartsAt()).isBetween(before, after);
            assertThat(created.getServiceUntil() - created.getStartsAt()).isEqualTo(365L * 86400L);
            assertThat(created.getGraceUntil() - created.getServiceUntil()).isEqualTo(7L * 86400L);
        }
    }

    @Test
    void defaultWindowDoesNotMoveWhenResignedAtAnotherTime() throws Exception {
        GrantRevisionEntity created = row();
        long startsAt = created.getStartsAt();
        long graceUntil = created.getGraceUntil();
        JsonNode first = compose(created, startsAt);
        JsonNode later = compose(created, startsAt + 180L * 86400L);
        assertThat(first.path("iat").asLong()).isNotEqualTo(later.path("iat").asLong());
        assertThat(first.path("nbf").asLong()).isEqualTo(startsAt);
        assertThat(later.path("nbf").asLong()).isEqualTo(startsAt);
        assertThat(first.path("exp").asLong()).isEqualTo(graceUntil);
        assertThat(later.path("exp").asLong()).isEqualTo(graceUntil);
        assertThat(created.getGraceUntil()).isEqualTo(graceUntil);
    }

    @Test
    void explicitFixedWindowRemainsEndedAfterDeadline() throws Exception {
        GrantRevisionEntity fixed = row().withValidity("FIXED", 1000L, 2000L, 2600L);
        ValidityPolicy loaded = registry(fixed).findById(fixed.getGrantId()).get().getValidity();
        assertThat(loaded.getStartsAt()).isEqualTo(1000L);
        assertThat(loaded.getCommercialGraceUntil()).isEqualTo(2600L);
        assertThat(loaded.commercialPhaseAt(5000L)).isEqualTo(Decision.CommercialPhase.ENDED);
        JsonNode resigned = compose(fixed, 5000L);
        assertThat(resigned.path("nbf").asLong()).isEqualTo(1000L);
        assertThat(resigned.path("exp").asLong()).isEqualTo(2600L);
    }

    @Test
    void legacyMissingWindowFailsBothReadAndIssuanceWithoutInventingDates() {
        GrantRevisionEntity legacy = row().withValidity(null, null, null, null);
        assertThatThrownBy(() -> registry(legacy).findById(legacy.getGrantId()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessage(GrantValidityConstant.MISSING_VALIDITY)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        assertThatThrownBy(() -> compose(legacy, 5000L)).isInstanceOf(LicenseHttpException.class)
                .hasMessage(GrantValidityConstant.MISSING_VALIDITY);
        assertThat(legacy.getStartsAt()).isNull();
        assertThat(legacy.getServiceUntil()).isNull();
        assertThat(legacy.getGraceUntil()).isNull();
    }

    @Test
    void partialUnknownAndContradictoryWindowsFailClosed() {
        for (GrantRevisionEntity invalid : Arrays.asList(
                row().withValidity("FIXED", 1000L, 2000L, null),
                row().withValidity("FIXED", 2000L, 1000L, 2600L),
                row().withValidity("FIXED", 1000L, 2000L, 1500L),
                row().withValidity("UNKNOWN", 1000L, 2000L, 2600L),
                row().withValidity("PERPETUAL", null, null, null),
                row().withValidity("PERPETUAL", 1000L, 0L, 0L),
                row().withValidity("PERPETUAL", 1000L, 2000L, 2600L))) {
            assertThatThrownBy(() -> GrantValidityHelper.fromGrant(invalid))
                    .isInstanceOf(LicenseHttpException.class)
                    .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        }
    }

    @Test
    void legitimatePerpetualWindowOmitsExpiryAndPassesOwnWireContract() throws Exception {
        GrantRevisionEntity perpetual = row().withValidity("PERPETUAL", 1000L, null, null);
        ValidityPolicy loaded = registry(perpetual).findById(perpetual.getGrantId()).get().getValidity();
        assertThat(loaded.getKind()).isEqualTo(ValidityPolicy.Kind.PERPETUAL);
        assertThat(loaded.commercialPhaseAt(1000000000L)).isEqualTo(Decision.CommercialPhase.ACTIVE);
        JsonNode json = compose(perpetual, 5000L);
        assertThat(json.has("exp")).isFalse();
        assertThat(json.path("validity").has("serviceUntil")).isFalse();
        assertThat(json.path("validity").has("commercialGraceUntil")).isFalse();
        Protocol2Payload payload = StrictJson.mapper().treeToValue(json, Protocol2PayloadWire.class).toPayload();
        assertThat(payload.getExpiresAt()).isNull();
        assertThat(payload.getValidity().getKind()).isEqualTo(ValidityPolicy.Kind.PERPETUAL);
    }

    private static JpaGrantRegistry registry(GrantRevisionEntity grant) {
        GrantRepository grants = mock(GrantRepository.class);
        when(grants.findById(grant.getGrantId())).thenReturn(Optional.of(grant));
        return new JpaGrantRegistry(grants, mock(RevocationRepository.class));
    }

    private static GrantRevisionEntity row() {
        return new GrantRevisionEntity("grant-1", "customer-1", "product-1", "DEPLOYMENT",
                "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null);
    }

    private static JsonNode compose(GrantRevisionEntity grant, long issuedAt) throws Exception {
        ProductEntity product = new ProductEntity("product-1", "issuer-1", "example", "示例产品",
                "[\"example.run\"]", "[]");
        return StrictJson.mapper().readTree(OfflineCredentialComposer.compose(grant, product,
                LicenseTestModels.publishedSnapshot(),
                "{\"kind\":\"DEPLOYMENT\",\"activationId\":\"activation-1\",\"activationGeneration\":1}",
                "credential-1", issuedAt));
    }
}
