package io.github.surezzzzzz.sdk.license.client.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 官方在线判定契约：合法未知阶段的拒绝可返回，坏允许结论和未知协议仍响亮失败。
 *
 * @author surezzzzzz
 */
class DecisionWireContractTest {

    @Test
    void explicitUnknownCommercialPhaseIsLegalOnlyForDeny() {
        Map<String, Object> wire = deny();
        ClientDecision decision = ClientResponseValidator.parseDecision(wire);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getCommercialPhase()).isNull();
        assertThat(decision.getReasonCategory()).isEqualTo("SUBJECT_INELIGIBLE");
        wire.put("allowed", true);
        wire.put("reasonCategory", "ENTITLED");
        reject(wire);
    }

    @Test
    void missingPhaseAndMalformedOutcomeAreNotACommercialDeny() {
        Map<String, Object> missingPhase = deny();
        missingPhase.remove("commercialPhase");
        reject(missingPhase);
        for (Object malformed : Arrays.asList("false", 0, null)) {
            Map<String, Object> wire = deny();
            wire.put("allowed", malformed);
            reject(wire);
        }
    }

    @Test
    void unknownPhaseReasonAndFieldAreProtocolFailures() {
        Map<String, Object> phase = deny();
        phase.put("commercialPhase", "UNKNOWN");
        reject(phase);
        Map<String, Object> reason = deny();
        reason.put("reasonCategory", "UNRECOGNIZED");
        reject(reason);
        Map<String, Object> field = deny();
        field.put("protocolExtension", true);
        reject(field);
    }

    @Test
    void responseMetadataAndConnectivityAreStrictlyValidated() {
        Map<String, Object> missingMetadata = deny();
        missingMetadata.remove("evaluatedAt");
        reject(missingMetadata);
        Map<String, Object> badConnectivity = deny();
        badConnectivity.put("connectivityPhase", "BROKEN");
        reject(badConnectivity);
        Map<String, Object> badTimestamp = deny();
        badTimestamp.put("evaluatedAt", -1L);
        reject(badTimestamp);
        Map<String, Object> emptyRequest = deny();
        emptyRequest.put("requestId", "");
        reject(emptyRequest);
    }

    @Test
    void versionsAndDeadlinesCannotBeTruncatedOrSilentlyDiscarded() {
        for (Object version : Arrays.asList(-1L, 1.5D, "7",
                new java.math.BigInteger("9223372036854775808"))) {
            Map<String, Object> wire = deny();
            wire.put("modelVersion", version);
            reject(wire);
        }
        Map<String, Object> allowed = deny();
        allowed.put("allowed", true);
        allowed.put("commercialPhase", "ACTIVE");
        allowed.put("reasonCategory", "ENTITLED");
        allowed.put("modelVersion", 7L);
        allowed.put("recheckAt", 1780000600L);
        addAuthorizationMetadata(allowed);
        ClientDecision result = ClientResponseValidator.parseDecision(allowed);
        assertThat(result.getModelVersion()).isEqualTo(7L);
        assertThat(result.getRecheckAt()).isEqualTo(1780000600L);
        Map<String, Object> safeIntegerVersion = new HashMap<String, Object>(allowed);
        safeIntegerVersion.put("modelVersion", 2147483648L);
        ClientDecision wideVersion = ClientResponseValidator.parseDecision(safeIntegerVersion);
        assertThat(wideVersion.getModelVersion()).isEqualTo(2147483648L);
        allowed.put("recheckAt", "1780000600");
        reject(allowed);
        allowed.put("recheckAt", -1L);
        reject(allowed);
        allowed.put("recheckAt", 253402300800L);
        reject(allowed);
    }

    private Map<String, Object> deny() {
        Map<String, Object> wire = new HashMap<String, Object>();
        wire.put("allowed", false);
        wire.put("action", "read");
        wire.put("modelVersion", 0L);
        wire.put("commercialPhase", null);
        wire.put("evaluatedAt", 1780000000L);
        wire.put("requestId", "request-test");
        wire.put("recheckAt", null);
        wire.put("reservationFenceReferences", new java.util.ArrayList<String>());
        wire.put("reasonCategory", "SUBJECT_INELIGIBLE");
        return wire;
    }

    private void addAuthorizationMetadata(Map<String, Object> wire) {
        wire.put("productId", "product-test");
        wire.put("grantId", "grant-test");
        Map<String, Object> binding = new HashMap<String, Object>();
        binding.put("kind", "PERSON");
        Map<String, Object> subjectRef = new HashMap<String, Object>();
        subjectRef.put("kind", "PERSON");
        subjectRef.put("sourceId", "iam");
        subjectRef.put("subjectId", "user-1");
        binding.put("subjectRef", subjectRef);
        wire.put("subjectBinding", binding);
        wire.put("grantRevision", 1L);
        wire.put("grantStateVersion", 1L);
    }

    private void reject(Map<String, Object> wire) {
        assertThatThrownBy(() -> ClientResponseValidator.parseDecision(wire))
                .isInstanceOf(ClientResponseValidator.ProtocolViolationException.class);
    }
}
