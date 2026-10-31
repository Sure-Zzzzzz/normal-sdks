package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;

/** 完整发布级额度模型测试资料；业务断言不以缺政策快照绕过实际校验。 */
final class QuotaTestFixture {

    private static final String BASE = "{\"fields\":{\"rights\":{\"limit\":{\"type\":\"INTEGER\","
            + "\"minimum\":0,\"maximum\":100000}},\"facts\":{}},"
            + "\"actions\":{\"run\":{\"bool\":true},\"read\":{\"bool\":true}},"
            + "\"subjectPolicy\":{\"allowedKinds\":[\"PERSON\",\"SERVICE\",\"DEPLOYMENT\"],"
            + "\"allowedEligibilityKinds\":[\"ASSIGNED_SUBJECTS\",\"ALLOCATED_DEPLOYMENTS\"],\"offlineProofProfiles\":[]},"
            + "\"validityPolicy\":{\"allowedKinds\":[\"FIXED\",\"PERPETUAL\"],\"minimumStartsAt\":1,"
            + "\"maximumStartsAt\":253402300799,\"minimumDurationSeconds\":1,\"maximumDurationSeconds\":86400,"
            + "\"maximumGraceSeconds\":86400,\"graceActions\":[\"read\"]},"
            + "\"connectivityPolicy\":{\"allowedModes\":[\"ONLINE\"]},"
            + "\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},\"devicePolicy\":{\"mode\":\"NONE\"},"
            + "\"organizationPolicy\":{\"required\":false},\"quotaDefinitions\":{},"
            + "\"quotaConstraints\":{\"run\":[\"metric-1\"]},\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},"
            + "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\"]}";

    private QuotaTestFixture() {
    }

    static String model(String kind, String enforcement, String window) {
        ObjectNode value = StrictJson.parse(BASE, ObjectNode.class);
        ObjectNode definition = value.with("quotaDefinitions").putObject("metric-1");
        definition.put("limitRight", "limit").put("kind", kind).put("enforcement", enforcement);
        if ("LOCAL".equals(enforcement)) {
            value.withArray("requiredCapabilities").add("quota-local/1");
        }
        if ("NAMED_SEAT".equals(kind)) {
            value.withArray("requiredCapabilities").add("named-seat/1");
        }
        if ("CONCURRENT".equals(kind) && "ONLINE".equals(enforcement)) {
            definition.put("maxReservationSeconds", 300);
        }
        if ("CONSUMPTION".equals(kind)) {
            definition.put("window", window);
            if ("FIXED_WINDOW".equals(window)) {
                definition.put("periodSeconds", 100);
            }
        }
        return ModelPolicyWire.validate(value, "model-1", 1L, null);
    }
}
