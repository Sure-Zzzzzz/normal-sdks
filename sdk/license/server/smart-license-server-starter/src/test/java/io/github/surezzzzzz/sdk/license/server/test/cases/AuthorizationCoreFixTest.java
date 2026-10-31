package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.service.JpaGrantRegistry;
import io.github.surezzzzzz.sdk.license.server.service.ModelBackedRulePort;
import io.github.surezzzzzz.sdk.license.server.service.OfflineCredentialComposer;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

/**
 * 授权业务核心修回归：①期限持久事实（创建校验+装载行值）②模型真求值
 * ③离线成品与 Verifier 契约对齐 ④签发真幂等（稳定摘要命中首任务）。
 *
 * @author surezzzzzz
 */
class AuthorizationCoreFixTest {

    private GrantRepository grants;
    private ModelVersionRepository models;
    private ProductRepository products;
    private LicenseGrantController controller;
    private SigningOperationRepository operations;

    @BeforeEach
    void setUp() {
        grants = Mockito.mock(GrantRepository.class);
        models = Mockito.mock(ModelVersionRepository.class);
        products = Mockito.mock(ProductRepository.class);
        operations = Mockito.mock(SigningOperationRepository.class);
        SigningProfileRepository profiles = Mockito.mock(SigningProfileRepository.class);
        RevocationRepository revocations = Mockito.mock(RevocationRepository.class);
        when(products.findById("prod-1")).thenReturn(Optional.of(product()));
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity model =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-validity", "mdl-validity", "prod-1", 1L,
                        LicenseTestModels.publishedRightsSnapshot());
        model.publish();
        when(models.findById("mv-validity")).thenReturn(Optional.of(model));
        controller = new LicenseGrantController(grants, products, profiles, operations, null,
                models, new io.github.surezzzzzz.sdk.license.server.service.JpaQuotaLedgerService(
                Mockito.mock(io.github.surezzzzzz.sdk.license.server.repository.QuotaBalanceRepository.class)), null);
    }

    private static ProductEntity product() {
        return new ProductEntity("prod-1", "iss-1", "channel-sys", "渠道管理系统",
                "[\"channel.create\",\"channel.read\"]", "[]");
    }

    private static String body(String extra) {
        return "{\"customerId\":\"cu-1\",\"productId\":\"prod-1\",\"subjectKind\":\"PERSON\""
                + ",\"eligibilityKind\":\"CUSTOMER_MEMBERS\",\"mode\":\"HYBRID\""
                + ",\"offlineProofProfile\":\"iam\",\"modelVersionId\":\"mv-validity\","
                + "\"rights\":{\"count\":1}" + extra + "}";
    }

    // ===== ① 期限持久事实 =====

    @Test
    void createGrantRejectsInconsistentValidityWindow() {
        assertThatThrownBy(() -> controller.createGrant(body(
                ",\"validity\":{\"kind\":\"FIXED\",\"startsAt\":100,\"serviceUntil\":50,\"commercialGraceUntil\":60}}"),
                "idem-v1", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("startsAt<serviceUntil");
        assertThatThrownBy(() -> controller.createGrant(body(
                ",\"validity\":{\"kind\":\"PERPETUAL\",\"startsAt\":100,\"serviceUntil\":200}}"),
                "idem-v2", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessageContaining("永久期限不得携带");
    }

    @Test
    void registryLoadsRowValidityNotRecomputed() {
        GrantRevisionEntity row = new GrantRevisionEntity("gr-v", "cu-1", "prod-1", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", null);
        row.withValidity("FIXED", 1000L, 2000L, 2600L);
        when(grants.findById("gr-v")).thenReturn(Optional.of(row));
        long before = java.time.Instant.now().getEpochSecond();
        JpaGrantRegistry registry = new JpaGrantRegistry(grants,
                Mockito.mock(RevocationRepository.class));
        io.github.surezzzzzz.sdk.license.server.entity.GrantAggregate aggregate =
                registry.findById("gr-v").orElseThrow(IllegalStateException::new);
        // 行值装载：与 now 无关（否则 startsAt 会≈before）
        assertThat(aggregate.getValidity().getStartsAt()).isEqualTo(1000L);
        assertThat(aggregate.getValidity().getServiceUntil()).isEqualTo(2000L);
        assertThat(aggregate.getValidity().getCommercialGraceUntil()).isEqualTo(2600L);
        assertThat(aggregate.getValidity().getStartsAt()).isLessThan(before);
    }

    // ===== ② 模型真求值 =====

    @Test
    void rulePortEvaluatesSavedModelTreeWithGrantRights() {
        // 模型：channel.create 当 channelCountAfter ≤ seatLimit（权益）
        String snapshot = "{\"fields\":{\"rights\":{\"seatLimit\":{\"type\":\"INTEGER\",\"required\":true"
                + ",\"minimum\":0,\"maximum\":100000}},\"facts\":{\"channelCountAfter\":{\"type\":\"INTEGER\""
                + ",\"required\":true,\"minimum\":0,\"maximum\":100000,\"source\":\"PRODUCT\"}}},"
                + "\"actions\":{\"channel.create\":{\"all\":[{\"compare\":{\"operator\":\"LTE\","
                + "\"left\":{\"ref\":\"facts.channelCountAfter\"},\"right\":{\"ref\":\"rights.seatLimit\"}}}]}}}";
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity model =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-1", "mdl-1", "prod-1", 1L, snapshot);
        model.publish();
        when(models.findById("mv-1")).thenReturn(Optional.of(model));
        GrantRevisionEntity row = new GrantRevisionEntity("gr-r", "cu-1", "prod-1", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", null, "mv-1", "{\"seatLimit\":100}");
        when(grants.findById("gr-r")).thenReturn(Optional.of(row));

        ModelBackedRulePort port = new ModelBackedRulePort(grants, models);
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("channelCountAfter", 80);
        assertThat(port.evaluate("gr-r", "channel.create", facts))
                .as("席位内应放行").isTrue();
        facts.put("channelCountAfter", 150);
        assertThat(port.evaluate("gr-r", "channel.create", facts))
                .as("超席位应拒绝（已保存模型进求值，恒真端口已废）").isFalse();
        assertThat(port.evaluate("gr-r", "channel.destroy", facts))
                .as("未声明动作应拒绝").isFalse();
    }

    @Test
    void rulePortCannotUseDeclaredActionsAsMissingModelSubstitute() {
        GrantRevisionEntity row = new GrantRevisionEntity("gr-n", "cu-1", "prod-1", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", null);
        when(grants.findById("gr-n")).thenReturn(Optional.of(row));
        ModelBackedRulePort port = new ModelBackedRulePort(grants, models);
        assertThatThrownBy(() -> port.evaluate("gr-n", "channel.create", new HashMap<String, Object>()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
    }

    @Test
    void missingOrCorruptPersistedRuleMaterialIsUnavailableRatherThanDenyOrAllow() {
        GrantRevisionEntity row = new GrantRevisionEntity("gr-r", "cu-1", "prod-1", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", null, "mv-1", "{}");
        when(grants.findById("gr-r")).thenReturn(Optional.of(row));
        ModelBackedRulePort port = new ModelBackedRulePort(grants, models);
        assertRuleUnavailable(port);
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity broken =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-1", "mdl-1", "prod-1", 7L, "{broken-json}");
        broken.publish();
        when(models.findById("mv-1")).thenReturn(Optional.of(broken));
        assertRuleUnavailable(port);
        io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity valid =
                new io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity(
                        "mv-1", "mdl-1", "prod-1", 7L,
                        "{\"fields\":{\"rights\":{},\"facts\":{}},\"actions\":{\"read\":{\"bool\":true}}}");
        valid.publish();
        when(models.findById("mv-1")).thenReturn(Optional.of(valid));
        when(grants.findById("gr-r")).thenReturn(Optional.of(new GrantRevisionEntity(
                "gr-r", "cu-1", "prod-1", "PERSON", "CUSTOMER_MEMBERS", null, "ONLINE", null,
                "mv-1", "{broken-rights}")));
        assertRuleUnavailable(port);
    }

    private void assertRuleUnavailable(ModelBackedRulePort port) {
        assertThatThrownBy(() -> port.evaluateResult("gr-r", "read", new HashMap<String, Object>()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
    }

    // ===== ③ 离线成品 ↔ Verifier 契约 =====

    @Test
    void composedPayloadPassesProtocolWireStructureValidation() throws Exception {
        GrantRevisionEntity deployment = new GrantRevisionEntity("gr-d", "cu-1", "prod-1",
                "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null, "mv-test", "{\"channelLimit\":100}");
        deployment.withValidity("FIXED", 1000L, 2000L, 2600L);
        String deploymentJson = OfflineCredentialComposer.compose(deployment, product(),
                LicenseTestModels.publishedSnapshot(),
                "{\"kind\":\"DEPLOYMENT\",\"activationId\":\"act-1\",\"activationGeneration\":2}",
                "cred-x", 1500L);
        // Verifier 侧 wire 契约：结构恰含键校验通过即证明签发端与 Core 对齐。
        io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire wire =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(deploymentJson,
                                io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire.class);
        io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload payload = wire.toPayload();
        assertThat(payload.getSubjectBinding().activationId()).isEqualTo("act-1");

        // PERSON 仍由 Composer 接收简化输入，但输出必须是协议要求的嵌套 subjectRef。
        String modelSnapshot = LicenseTestModels.publishedAssignedPersonSnapshot();
        GrantRevisionEntity person = new GrantRevisionEntity("gr-p", "cu-1", "prod-1", "PERSON",
                "ASSIGNED_SUBJECTS", "iam", "OFFLINE", null, "mv-9",
                "{\"channelLimit\":100}");
        person.withValidity("FIXED", 1000L, 2000L, 2600L);
        String personJson = OfflineCredentialComposer.compose(person, product(), modelSnapshot,
                "{\"kind\":\"PERSON\",\"sourceId\":\"iam\",\"subjectId\":\"u-1\","
                        + "\"assignmentId\":\"assignment-1\",\"assignmentGeneration\":2}", "cred-y", 1500L);
        io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire personWire =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(personJson,
                                io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire.class);
        io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload personPayload =
                personWire.toPayload();
        assertThat(personPayload.getRights()).containsEntry("channelLimit", 100);
        assertThat(personPayload.getSubjectBinding().subjectRef().getSubjectId()).isEqualTo("u-1");
        assertThat(personPayload.getSubjectBinding().subjectRef().getSourceId()).isEqualTo("iam");
        assertThat(personPayload.getSubjectBinding().assignmentId()).contains("assignment-1");
        assertThat(personPayload.getSubjectBinding().assignmentGeneration().getAsLong()).isEqualTo(2L);
        assertThat(personPayload.getModelSnapshot().get("actions"))
                .as("已保存模型快照原文入载荷").isNotNull();
    }

    // ===== ④ 签发真幂等 =====

    @Test
    void issuanceRetryWithSameKeyReturnsFirstTask() throws Exception {
        io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity first =
                new io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity(
                        "op-1", "cred-first", "gr-i", "sp-1", "h", "p", "key-1:gr-i");
        first.setState("READY");
        first.setCommittedSignature("sig");
        when(grants.findById("gr-i")).thenReturn(Optional.of(new GrantRevisionEntity(
                "gr-i", "cu-1", "prod-1", "DEPLOYMENT", "ALLOCATED_DEPLOYMENTS", null, "OFFLINE", null)));
        when(operations.findByIdempotencyDigest("key-1:gr-i")).thenReturn(first);
        ResponseEntity<String> retried = controller.requestCredential("gr-i",
                "{\"subjectRef\":\"{}\",\"kid\":\"k\"}", "key-1",
                TestPlans.all("issue"),
                LicenseIdentityHttpTest.TestRequests.authenticated());
        // 命中首任务：202 + 首任务凭据编号（不生成新任务/新凭据）
        assertThat(retried.getStatusCodeValue()).isEqualTo(202);
        assertThat(retried.getBody()).contains("cred-first");
    }
}
