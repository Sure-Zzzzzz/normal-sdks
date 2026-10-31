package io.github.surezzzzzz.sdk.license.server.test.cases;

/** License 2.0 测试用完整已发布模型快照，避免用残缺 JSON 冒充生产签发事实。 */
final class LicenseTestModels {

    private LicenseTestModels() {
    }

    /** 可被 ModelPolicyWire 与 OfflineCredentialComposer 同时消费的固定模型。 */
    static String publishedSnapshot() {
        return "{\"id\":\"model-test\",\"version\":1,"
                + "\"ruleProtocol\":\"license-condition\",\"ruleVersion\":1,"
                + "\"fields\":{\"rights\":{\"channelLimit\":{\"type\":\"INTEGER\","
                + "\"required\":true,\"minimum\":0,\"maximum\":100000}},"
                + "\"facts\":{\"channelCountAfter\":{\"type\":\"INTEGER\","
                + "\"required\":true,\"minimum\":0,\"maximum\":100000,\"source\":\"PRODUCT\"}}},"
                + "\"actions\":{\"channel.create\":{\"all\":[{\"compare\":{"
                + "\"operator\":\"LTE\",\"left\":{\"ref\":\"facts.channelCountAfter\"},"
                + "\"right\":{\"ref\":\"rights.channelLimit\"}}}]}},"
                + "\"subjectPolicy\":{\"allowedKinds\":[\"DEPLOYMENT\"],"
                + "\"allowedEligibilityKinds\":[\"ALLOCATED_DEPLOYMENTS\"],"
                + "\"offlineProofProfiles\":[]},"
                + "\"validityPolicy\":{\"allowedKinds\":[\"FIXED\",\"PERPETUAL\"],"
                + "\"minimumStartsAt\":1,\"maximumStartsAt\":253402300799,"
                + "\"minimumDurationSeconds\":1,\"maximumDurationSeconds\":315360000,"
                + "\"maximumGraceSeconds\":315360000,\"graceActions\":[\"channel.create\"]},"
                + "\"connectivityPolicy\":{\"allowedModes\":[\"OFFLINE\"]},"
                + "\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},"
                + "\"devicePolicy\":{\"mode\":\"NONE\"},"
                + "\"organizationPolicy\":{\"required\":false},"
                + "\"quotaDefinitions\":{},\"quotaConstraints\":{},"
                + "\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},"
                + "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\",\"clock-state/1\"]}";
    }

    /** 多类型权益测试使用的完整模型。 */
    static String publishedRightsSnapshot() {
        return "{\"id\":\"model-test\",\"version\":1,"
                + "\"ruleProtocol\":\"license-condition\",\"ruleVersion\":1,"
                + "\"fields\":{\"rights\":{"
                + "\"count\":{\"type\":\"INTEGER\",\"required\":true,\"minimum\":0,\"maximum\":100},"
                + "\"enabled\":{\"type\":\"BOOLEAN\"},"
                + "\"tier\":{\"type\":\"STRING\",\"maxLength\":5,\"enumValues\":[\"basic\",\"pro\"]},"
                + "\"regions\":{\"type\":\"STRING_SET\",\"maxItems\":2}},\"facts\":{}},"
                + "\"actions\":{\"use\":{\"bool\":true}},"
                + "\"subjectPolicy\":{\"allowedKinds\":[\"PERSON\"],"
                + "\"allowedEligibilityKinds\":[\"CUSTOMER_MEMBERS\"],\"offlineProofProfiles\":[\"iam\"]},"
                + "\"validityPolicy\":{\"allowedKinds\":[\"FIXED\",\"PERPETUAL\"],"
                + "\"minimumStartsAt\":1,\"maximumStartsAt\":253402300799,"
                + "\"minimumDurationSeconds\":1,\"maximumDurationSeconds\":315360000,"
                + "\"maximumGraceSeconds\":315360000,\"graceActions\":[\"use\"]},"
                + "\"connectivityPolicy\":{\"allowedModes\":[\"HYBRID\"],\"refreshIntervalSeconds\":60,"
                + "\"leaseDurationSeconds\":300,\"outageDurationSeconds\":600,\"outageActions\":[\"use\"]},"
                + "\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},"
                + "\"devicePolicy\":{\"mode\":\"NONE\"},"
                + "\"organizationPolicy\":{\"required\":true,\"maxObservationAgeSeconds\":600,"
                + "\"membershipLeaseSeconds\":300},"
                + "\"quotaDefinitions\":{},\"quotaConstraints\":{},"
                + "\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},"
                + "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\",\"clock-state/1\","
                + "\"hybrid-lease/1\",\"organization-lease/1\",\"subject-proof/iam\"]}";
    }

    /** 在线固定资格实名额度模型。 */
    static String publishedNamedSeatSnapshot() {
        return publishedSnapshot()
                .replace("\"allowedKinds\":[\"DEPLOYMENT\"]",
                        "\"allowedKinds\":[\"PERSON\"]")
                .replace("\"allowedEligibilityKinds\":[\"ALLOCATED_DEPLOYMENTS\"]",
                        "\"allowedEligibilityKinds\":[\"ASSIGNED_SUBJECTS\"]")
                .replace("\"allowedModes\":[\"OFFLINE\"]", "\"allowedModes\":[\"ONLINE\"]")
                .replace("\"quotaDefinitions\":{}",
                        "\"quotaDefinitions\":{\"named_seats\":{\"limitRight\":\"channelLimit\","
                                + "\"kind\":\"NAMED_SEAT\",\"enforcement\":\"ONLINE\"}}")
                .replace("\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\",\"clock-state/1\"]",
                        "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\",\"named-seat/1\"]");
    }

    /** 人员离线固定资格模型：真实资格引用配受控身份能力档案，不借部署或组织成员政策。 */
    static String publishedAssignedPersonSnapshot() {
        return publishedSnapshot()
                .replace("\"allowedKinds\":[\"DEPLOYMENT\"]", "\"allowedKinds\":[\"PERSON\"]")
                .replace("\"allowedEligibilityKinds\":[\"ALLOCATED_DEPLOYMENTS\"]",
                        "\"allowedEligibilityKinds\":[\"ASSIGNED_SUBJECTS\"]")
                .replace("\"offlineProofProfiles\":[]", "\"offlineProofProfiles\":[\"iam\"]")
                .replace("\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\",\"clock-state/1\"]",
                        "\"requiredCapabilities\":[\"license-condition/1\",\"subject-binding/1\","
                                + "\"clock-state/1\",\"subject-proof/iam\"]");
    }
}
