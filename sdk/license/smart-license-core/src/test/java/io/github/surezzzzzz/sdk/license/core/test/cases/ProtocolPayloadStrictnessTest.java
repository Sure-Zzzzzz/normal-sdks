package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire;
import io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.math.BigInteger;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * 协议快照严格性：嵌套整数、联合政策、额度引用和通过校验后的不可变性。
 *
 * @author surezzzzzz
 */
@Slf4j
class ProtocolPayloadStrictnessTest {

    private ObjectNode base() throws Exception {
        return (ObjectNode) StrictJson.mapper().readTree("{"
                + "\"protocolVersion\":2,\"credentialKind\":\"OFFLINE\","
                + "\"jti\":\"credential\",\"iss\":\"issuer\",\"aud\":\"product\","
                + "\"customerId\":\"customer\",\"grantId\":\"grant\","
                + "\"grantRevision\":1,\"grantStateVersion\":1,"
                + "\"eligibilityPolicy\":{\"kind\":\"ALLOCATED_DEPLOYMENTS\"},"
                + "\"subjectBinding\":{\"kind\":\"DEPLOYMENT\",\"activationId\":\"activation\","
                + "\"activationGeneration\":1},\"iat\":100,\"nbf\":100,\"exp\":300,"
                + "\"validity\":{\"kind\":\"FIXED\",\"startsAt\":100,\"serviceUntil\":200,"
                + "\"commercialGraceUntil\":300,\"graceActions\":[]},"
                + "\"clockPolicy\":{\"skewSeconds\":0,\"rollbackToleranceSeconds\":0},"
                + "\"deviceBinding\":{\"mode\":\"NONE\"},\"organizationPolicy\":{\"required\":false},"
                + "\"revocationPolicy\":{\"mode\":\"OPTIONAL\"},\"rights\":{\"capacity\":100},"
                + "\"quotaPolicy\":{\"definitions\":{},\"constraints\":{},\"windowAnchors\":{}},"
                + "\"quotaAllocations\":[],\"namedSeatReferences\":[],\"requiredCapabilities\":[]}");
    }

    private Protocol2PayloadWire wire(ObjectNode node) {
        return StrictJson.parse(node.toString(), Protocol2PayloadWire.class);
    }

    private ObjectNode localQuota() throws Exception {
        ObjectNode node = base();
        ObjectNode definitions = (ObjectNode) node.path("quotaPolicy").path("definitions");
        ObjectNode metric = definitions.putObject("requests");
        metric.put("limitRight", "capacity");
        metric.put("kind", "CONSUMPTION");
        metric.put("enforcement", "LOCAL");
        metric.put("window", "TOTAL");
        ObjectNode allocation = node.withArray("quotaAllocations").addObject();
        allocation.put("allocationId", "allocation");
        allocation.put("metric", "requests");
        allocation.put("quantity", 20);
        allocation.put("grantRevision", 1);
        allocation.put("retention", "FIXED");
        allocation.put("retainUntil", 300);
        return node;
    }

    @Test
    void nestedNumbersRejectDecimalsAndBigIntegerWrapping() throws Exception {
        ObjectNode node = base();
        ((ObjectNode) node.path("subjectBinding")).put("activationGeneration", 1.5);
        log.info("验证嵌套代数的小数和安全整数溢出均拒绝");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        ((ObjectNode) node.path("subjectBinding")).put("activationGeneration", new BigInteger("18446744073709551617"));
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        ((ObjectNode) node.path("subjectBinding")).put("activationGeneration", 9007199254740992L);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        ((ObjectNode) node.path("subjectBinding")).put("activationGeneration", 1);
        ((ObjectNode) node.path("clockPolicy")).put("skewSeconds", 0.5);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void actionSetsRejectWrongTypesAndDuplicates() throws Exception {
        ObjectNode node = base();
        ObjectNode validity = (ObjectNode) node.path("validity");
        validity.put("graceActions", "read");
        log.info("验证动作集合的形态与元素类型严格性");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        validity.putArray("graceActions").add(1);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        validity.putArray("graceActions").add("read").add("read");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void scalarCoercionAndExplicitNullExpiryAreRejected() throws Exception {
        ObjectNode node = base();
        node.put("grantRevision", "1");
        log.info("验证协议整数不接受数字字符串，能力文本不接受数字，exp不接受显式null");
        assertThatThrownBy(() -> wire(node)).isInstanceOf(IllegalArgumentException.class);
        node.put("grantRevision", 1);
        node.withArray("requiredCapabilities").add(1);
        assertThatThrownBy(() -> wire(node)).isInstanceOf(IllegalArgumentException.class);
        node.withArray("requiredCapabilities").removeAll();
        node.putNull("exp");
        assertThatThrownBy(() -> wire(node)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maintenanceTextAndOutOfRangeEpochAreRejected() throws Exception {
        ObjectNode node = base();
        ObjectNode validity = (ObjectNode) node.path("validity");
        validity.put("maintenanceUntil", "200");
        log.info("验证可选维护截止仍必须是整数秒，不能静默丢弃非法值");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        validity.put("maintenanceUntil", 253402300800L);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void localAllocationCannotExceedSignedRightsLimitOrDisappear() throws Exception {
        ObjectNode node = localQuota();
        ((ObjectNode) node.path("quotaAllocations").get(0)).put("quantity", 101);
        log.info("验证部署分配必须存在且不超过签入的指标权益限额");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        node.withArray("quotaAllocations").removeAll();
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void unknownRevocationModeAndOrganizationFlagAreRejected() throws Exception {
        ObjectNode node = base();
        ((ObjectNode) node.path("revocationPolicy")).put("mode", "DISABLED");
        log.info("验证未知吊销模式不会降为可选，组织标记不做字符串转换");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        ((ObjectNode) node.path("revocationPolicy")).put("mode", "OPTIONAL");
        ((ObjectNode) node.path("organizationPolicy")).put("required", "false");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void deploymentCannotBorrowAssignedEligibility() throws Exception {
        ObjectNode node = base();
        ObjectNode eligibility = (ObjectNode) node.path("eligibilityPolicy");
        eligibility.put("kind", "ASSIGNED_SUBJECTS");
        eligibility.put("offlineProofProfile", "identity/1");
        log.info("验证部署对象不能借用人员固定资格政策");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void quotaAllocationNumbersAndRetentionAreStrict() throws Exception {
        ObjectNode node = localQuota();
        ObjectNode allocation = (ObjectNode) node.path("quotaAllocations").get(0);
        allocation.put("quantity", 1.5);
        log.info("验证额度分配不截断数量，不允许保留截止早于文件截止");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        allocation.put("quantity", 20);
        allocation.put("retainUntil", 299);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        allocation.put("retainUntil", 300);
        allocation.put("grantRevision", 2);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        allocation.put("grantRevision", 1);
        allocation.put("metric", "unknown");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void fixedWindowsRequireExactlyTheirPersistentAnchors() throws Exception {
        ObjectNode node = localQuota();
        ObjectNode definition = (ObjectNode) node.path("quotaPolicy").path("definitions").path("requests");
        definition.put("window", "FIXED_WINDOW");
        definition.put("periodSeconds", 60);
        log.info("验证固定窗口锚点必须恰好对应定义，且不得用小数");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        ObjectNode anchors = (ObjectNode) node.path("quotaPolicy").path("windowAnchors");
        anchors.put("requests", 100.5);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
        anchors.put("requests", 100);
        assertThat(wire(node).toPayload().getQuotaAllocations()).hasSize(1);
        anchors.put("extra", 100);
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void modelSnapshotRejectsFloatingAndUnsafeNumbers() throws Exception {
        ObjectNode node = base();
        ObjectNode model = node.putObject("model");
        model.put("version", 1);
        ObjectNode fields = model.putObject("fields");
        ObjectNode rights = fields.putObject("rights");
        ObjectNode limit = rights.putObject("limit");
        limit.put("type", "INTEGER");
        limit.put("minimum", 0.5);
        limit.put("maximum", 10);
        ObjectNode facts = fields.putObject("facts");
        ObjectNode actions = model.putObject("actions");
        ObjectNode action = actions.putObject("use");
        ObjectNode compare = action.putObject("compare");
        compare.put("operator", "LTE");
        compare.putObject("left").put("ref", "rights.limit");
        compare.putObject("right").put("literal", 1);
        log.info("验证模型快照字段范围的小数被拒绝");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);

        limit.put("minimum", 0);
        limit.put("maximum", new BigInteger("9007199254740992"));
        log.info("验证模型快照字段范围超过 JSON 安全整数上限被拒绝");
        assertThatThrownBy(() -> wire(node).toPayload()).isInstanceOf(LicenseCoreException.class);

        limit.put("maximum", 10);
        assertThatCode(() -> RuleModelWire.fromSnapshot(wire(node).toPayload())).doesNotThrowAnyException();
    }

    @Test
    void validatedQuotaSnapshotCannotBeMutatedThroughOriginalOrGetter() throws Exception {
        Protocol2PayloadWire wire = wire(localQuota());
        Protocol2Payload payload = wire.toPayload();
        wire.quotaAllocations.get(0).put("quantity", 99L);
        wire.requiredCapabilities.add("unexpected/1");
        log.info("验证签名快照深复制及只读返回，不共享校验前的可变分配集合");
        assertThat(payload.getQuotaAllocations().get(0).get("quantity")).isEqualTo(20);
        assertThat(payload.getRequiredCapabilities()).isEmpty();
        assertThatThrownBy(() -> payload.getQuotaAllocations().get(0).put("quantity", 99L))
                .isInstanceOf(UnsupportedOperationException.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> definitions = (Map<String, Object>) payload.getQuotaPolicy().get("definitions");
        assertThatThrownBy(() -> definitions.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
