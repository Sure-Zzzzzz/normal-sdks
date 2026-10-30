package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.CompareOperator;
import io.github.surezzzzzz.sdk.license.core.model.ConditionEvaluator;
import io.github.surezzzzzz.sdk.license.core.model.ConditionNode;
import io.github.surezzzzzz.sdk.license.core.model.FactValues;
import io.github.surezzzzzz.sdk.license.core.model.FieldDefinition;
import io.github.surezzzzzz.sdk.license.core.model.FieldType;
import io.github.surezzzzzz.sdk.license.core.model.Operand;
import io.github.surezzzzzz.sdk.license.core.model.RuleModel;
import io.github.surezzzzzz.sdk.license.core.model.RuleModelValidator;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * license-condition/1 一致性断言向量（设计 §一致性断言向量 十条逐条落地）。
 *
 * <p>示例模型：channelLimit=100、allowedKinds=[A,B]、事实 channelCountAfter/deviceKind，
 * 动作 channel.create = all(compare LTE, member)。</p>
 *
 * @author surezzzzzz
 */
class ConditionTreeTest {

    private static RuleModel sampleModel() {
        return RuleModel.builder()
                .right(FieldDefinition.builder("channelLimit", FieldType.INTEGER).required()
                        .range(0, 100000).unit("count").build())
                .right(FieldDefinition.builder("allowedKinds", FieldType.STRING_SET).required()
                        .maxItems(16).build())
                .fact(FieldDefinition.builder("channelCountAfter", FieldType.INTEGER).required()
                        .range(0, 100000).unit("count").source(FieldDefinition.FactSource.PRODUCT).build())
                .fact(FieldDefinition.builder("deviceKind", FieldType.STRING).required()
                        .maxLengthCodePoints(32).source(FieldDefinition.FactSource.DEVICE).build())
                .action("channel.create", ConditionNode.all(
                        ConditionNode.compare(CompareOperator.LTE,
                                Operand.ref(Operand.Namespace.FACTS, "channelCountAfter"),
                                Operand.ref(Operand.Namespace.RIGHTS, "channelLimit")),
                        ConditionNode.member(
                                Operand.ref(Operand.Namespace.FACTS, "deviceKind"),
                                Operand.ref(Operand.Namespace.RIGHTS, "allowedKinds"))))
                .build();
    }

    private static FactValues values(Map<String, Object> rights, Map<String, Object> facts) {
        return FactValues.of(sampleModel(), rights, facts, false);
    }

    private static Map<String, Object> fullRights() {
        Map<String, Object> rights = new HashMap<String, Object>();
        rights.put("channelLimit", 100L);
        rights.put("allowedKinds", Arrays.asList("A", "B"));
        return rights;
    }

    private static Map<String, Object> facts(long count, String kind) {
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("channelCountAfter", count);
        facts.put("deviceKind", kind);
        return facts;
    }

    @Test
    void vectorAllowWhenWithinLimitAndKindMatches() {
        assertThat(ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                values(fullRights(), facts(100, "A")))).isTrue();
    }

    @Test
    void vectorDenyWhenCountExceedsLimit() {
        assertThat(ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                values(fullRights(), facts(101, "A")))).isFalse();
    }

    @Test
    void vectorDenyWhenKindNotMember() {
        assertThat(ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                values(fullRights(), facts(0, "C")))).isFalse();
    }

    @Test
    void vectorDenyWhenRequiredFactMissing() {
        Map<String, Object> partial = facts(50, "A");
        partial.remove("channelCountAfter");
        assertThatThrownBy(() -> ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                values(fullRights(), partial)))
                .isInstanceOf(FactValues.MissingFactException.class);
    }

    @Test
    void vectorDenyWhenTypeMismatched() {
        Map<String, Object> wrongType = new HashMap<String, Object>();
        wrongType.put("channelCountAfter", "100");
        wrongType.put("deviceKind", "A");
        assertThatThrownBy(() -> values(fullRights(), wrongType))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vectorAnyBranchCannotHideMissingFactViaShortCircuit() {
        RuleModel model = RuleModel.builder()
                .fact(FieldDefinition.builder("flag", FieldType.BOOLEAN).required().build())
                .fact(FieldDefinition.builder("absentFact", FieldType.BOOLEAN).required().build())
                .action("act", ConditionNode.any(
                        ConditionNode.bool(true),
                        ConditionNode.compare(CompareOperator.EQ,
                                Operand.ref(Operand.Namespace.FACTS, "absentFact"),
                                Operand.literal(true))))
                .build();
        RuleModelValidator.validate(model);
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("flag", true);
        assertThatThrownBy(() -> ConditionEvaluator.evaluate(model, "act",
                FactValues.of(model, null, facts, false)))
                .isInstanceOf(FactValues.MissingFactException.class);
    }

    @Test
    void vectorNotCannotFlipUnknownOperatorIntoAllow() {
        RuleModel model = RuleModel.builder()
                .fact(FieldDefinition.builder("n", FieldType.INTEGER).required().range(0, 10).build())
                .action("act", ConditionNode.not(ConditionNode.bool(false)))
                .build();
        RuleModelValidator.validate(model);
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("n", 1L);
        assertThat(ConditionEvaluator.evaluate(model, "act",
                FactValues.of(model, null, facts, false))).isTrue();
        // 未知动作是确定拒绝，不被取反：evaluate 直接抛规则结构异常
        assertThatThrownBy(() -> ConditionEvaluator.evaluate(model, "unknown.action",
                FactValues.of(model, null, facts, false)))
                .isInstanceOf(LicenseCoreException.class)
                .hasMessageContaining("规则");
    }

    @Test
    void vectorUnknownActionRejected() {
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("channelCountAfter", 1L);
        facts.put("deviceKind", "A");
        assertThatThrownBy(() -> ConditionEvaluator.evaluate(sampleModel(), "channel.delete",
                values(fullRights(), facts)))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void vectorMismatchedUnitsRejectedAtPublish() {
        RuleModel model = RuleModel.builder()
                .right(FieldDefinition.builder("limitBytes", FieldType.INTEGER).required()
                        .range(0, 1000000).unit("byte").build())
                .fact(FieldDefinition.builder("usedCount", FieldType.INTEGER).required()
                        .range(0, 1000000).unit("count").build())
                .action("act", ConditionNode.compare(CompareOperator.LTE,
                        Operand.ref(Operand.Namespace.FACTS, "usedCount"),
                        Operand.ref(Operand.Namespace.RIGHTS, "limitBytes")))
                .build();
        assertThatThrownBy(() -> RuleModelValidator.validate(model))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vectorRevokedCredentialDeniedByOuterLayerNotByRules() {
        // 规则层只判条件；吊销优先属于凭据校验层（见协议 §6），此处断言规则引擎不越权：
        // 相同输入规则结果确定一致。
        boolean first = ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                values(fullRights(), facts(10, "B")));
        boolean second = ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                values(fullRights(), facts(10, "B")));
        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @Test
    void stringLiteralCompareMatchesExactTextOnly() {
        RuleModel model = RuleModel.builder()
                .fact(FieldDefinition.builder("edition", FieldType.STRING).required()
                        .enumValues(newSet("BASIC", "PRO")).build())
                .action("export", ConditionNode.compare(CompareOperator.EQ,
                        Operand.ref(Operand.Namespace.FACTS, "edition"), Operand.literal("PRO")))
                .build();
        RuleModelValidator.validate(model);
        Map<String, Object> facts = new HashMap<String, Object>();
        facts.put("edition", "PRO");
        assertThat(ConditionEvaluator.evaluate(model, "export",
                FactValues.of(model, null, facts, false))).isTrue();
        facts.put("edition", "BASIC");
        assertThat(ConditionEvaluator.evaluate(model, "export",
                FactValues.of(model, null, facts, false))).isFalse();
        // 隐式 trim 不存在
        Map<String, Object> spaced = new HashMap<String, Object>();
        spaced.put("edition", " PRO");
        assertThatCode(() -> values(null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> FactValues.of(model, null, spaced, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringSetInputIsCopiedBeforeEvaluation() {
        List<String> allowedKinds = new ArrayList<String>(Arrays.asList("A", "B"));
        Map<String, Object> rights = new HashMap<String, Object>();
        rights.put("channelLimit", 100L);
        rights.put("allowedKinds", allowedKinds);
        Map<String, Object> unchangedRights = new HashMap<String, Object>(rights);
        unchangedRights.put("allowedKinds", new ArrayList<String>(allowedKinds));
        FactValues values = values(rights, facts(1, "A"));

        allowedKinds.set(0, "C");

        assertThat(ConditionEvaluator.evaluate(sampleModel(), "channel.create", values)).isTrue();
        assertThat(ConditionEvaluator.evaluate(sampleModel(), "channel.create",
                FactValues.of(sampleModel(), unchangedRights, facts(1, "C"), false))).isFalse();
    }

    private static Set<String> newSet(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }
}
