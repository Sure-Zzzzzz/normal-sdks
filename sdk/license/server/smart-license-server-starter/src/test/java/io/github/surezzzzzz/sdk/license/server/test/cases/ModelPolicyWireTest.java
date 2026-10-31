package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 模型完整政策快照的发布级边界。 */
class ModelPolicyWireTest {

    @Test
    void addsServerModelIdentityAndKeepsExplicitPolicies() throws Exception {
        String snapshot = "{\"fields\":{\"rights\":{},\"facts\":{}},"
                + "\"actions\":{\"read\":{\"bool\":true}},"
                + "\"subjectPolicy\":{\"allowedKinds\":[\"DEPLOYMENT\"],"
                + "\"allowedEligibilityKinds\":[\"ALLOCATED_DEPLOYMENTS\"],\"offlineProofProfiles\":[]},"
                + "\"validityPolicy\":{\"allowedKinds\":[\"FIXED\"],\"minimumStartsAt\":1,"
                + "\"maximumStartsAt\":253402300799,\"minimumDurationSeconds\":1,"
                + "\"maximumDurationSeconds\":86400,\"maximumGraceSeconds\":86400,\"graceActions\":[\"read\"]},"
                + "\"connectivityPolicy\":{\"allowedModes\":[\"ONLINE\"]},"
                + "\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},"
                + "\"devicePolicy\":{\"mode\":\"NONE\"},"
                + "\"organizationPolicy\":{\"required\":false},"
                + "\"quotaDefinitions\":{},\"quotaConstraints\":{},"
                + "\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},"
                + "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\"]}";

        String normalized = ModelPolicyWire.validate(
                io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(snapshot),
                "model-1", 7L, "[\"license-condition/1\",\"subject-binding/1\"]");

        assertThat(io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(normalized)
                .path("id").asText()).isEqualTo("model-1");
        assertThat(io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(normalized)
                .path("version").asLong()).isEqualTo(7L);
    }

    @Test
    void rejectsDevicePolicyWithoutDeclaredCapability() throws Exception {
        String snapshot = valid().replace("{\"mode\":\"NONE\"}",
                "{\"mode\":\"WEIGHTED\",\"profileVersion\":\"hardware/1\","
                        + "\"components\":{\"systemUuid\":50},\"minimumAvailableWeight\":1,"
                        + "\"maximumMismatchWeight\":0}");
        assertThatThrownBy(() -> ModelPolicyWire.validate(
                io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(snapshot),
                "model-1", 1L, "[\"license-condition/1\",\"subject-binding/1\"]"))
                .isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void rejectsLocalQuotaForNonDeploymentPolicy() throws Exception {
        String snapshot = valid()
                .replace("\"quotaDefinitions\":{}", "\"quotaDefinitions\":{\"local\":{\"limitRight\":\"seatLimit\",\"kind\":\"CONSUMPTION\",\"enforcement\":\"LOCAL\",\"window\":\"TOTAL\"}}")
                .replace("\"fields\":{\"rights\":{}", "\"fields\":{\"rights\":{\"seatLimit\":{\"type\":\"INTEGER\"}}}");
        assertThatThrownBy(() -> ModelPolicyWire.validate(
                io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(snapshot),
                "model-1", 1L, "[\"license-condition/1\",\"subject-binding/1\"]"))
                .isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void rejectsNamedSeatWithoutOnlineAssignedSubject() throws Exception {
        String snapshot = valid()
                .replace("\"quotaDefinitions\":{}", "\"quotaDefinitions\":{\"seat\":{\"limitRight\":\"seatLimit\",\"kind\":\"NAMED_SEAT\",\"enforcement\":\"LOCAL\"}}")
                .replace("\"fields\":{\"rights\":{}", "\"fields\":{\"rights\":{\"seatLimit\":{\"type\":\"INTEGER\"}}}");
        assertThatThrownBy(() -> ModelPolicyWire.validate(
                io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(snapshot),
                "model-1", 1L, "[\"license-condition/1\",\"subject-binding/1\"]"))
                .isInstanceOf(LicenseHttpException.class);
    }

    @Test
    void rejectsReservationWindowOnConsumptionQuota() throws Exception {
        String snapshot = valid()
                .replace("\"quotaDefinitions\":{}", "\"quotaDefinitions\":{\"usage\":{\"limitRight\":\"usageLimit\",\"kind\":\"CONSUMPTION\",\"enforcement\":\"ONLINE\",\"window\":\"TOTAL\",\"maxReservationSeconds\":10}}")
                .replace("\"fields\":{\"rights\":{}", "\"fields\":{\"rights\":{\"usageLimit\":{\"type\":\"INTEGER\"}}}");
        assertThatThrownBy(() -> ModelPolicyWire.validate(
                io.github.surezzzzzz.sdk.license.core.codec.StrictJson.mapper().readTree(snapshot),
                "model-1", 1L, "[\"license-condition/1\",\"subject-binding/1\"]"))
                .isInstanceOf(LicenseHttpException.class);
    }

    private static String valid() {
        return "{\"fields\":{\"rights\":{},\"facts\":{}},\"actions\":{\"read\":{\"bool\":true}},"
                + "\"subjectPolicy\":{\"allowedKinds\":[\"DEPLOYMENT\"],"
                + "\"allowedEligibilityKinds\":[\"ALLOCATED_DEPLOYMENTS\"],\"offlineProofProfiles\":[]},"
                + "\"validityPolicy\":{\"allowedKinds\":[\"FIXED\"],\"minimumStartsAt\":1,"
                + "\"maximumStartsAt\":253402300799,\"minimumDurationSeconds\":1,"
                + "\"maximumDurationSeconds\":86400,\"maximumGraceSeconds\":86400,\"graceActions\":[]},"
                + "\"connectivityPolicy\":{\"allowedModes\":[\"ONLINE\"]},"
                + "\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},"
                + "\"devicePolicy\":{\"mode\":\"NONE\"},\"organizationPolicy\":{\"required\":false},"
                + "\"quotaDefinitions\":{},\"quotaConstraints\":{},\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},"
                + "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\"]}";
    }
}
