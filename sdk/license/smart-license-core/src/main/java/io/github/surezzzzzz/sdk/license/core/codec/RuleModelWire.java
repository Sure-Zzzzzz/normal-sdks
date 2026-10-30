package io.github.surezzzzzz.sdk.license.core.codec;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.CompareOperator;
import io.github.surezzzzzz.sdk.license.core.model.ConditionNode;
import io.github.surezzzzzz.sdk.license.core.model.FieldDefinition;
import io.github.surezzzzzz.sdk.license.core.model.FieldType;
import io.github.surezzzzzz.sdk.license.core.model.Operand;
import io.github.surezzzzzz.sdk.license.core.model.RuleModel;
import io.github.surezzzzzz.sdk.license.core.model.RuleModelValidator;
import io.github.surezzzzzz.sdk.license.core.support.ProtocolValueHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型快照 JSON 线形态接线：payload.model（fields/actions）→ 类型化 RuleModel。
 *
 * <p>条件树节点恰一键；操作数 ref/literal 严格区分；字段类型/范围/单位声明式装载。</p>
 *
 * @author surezzzzzz
 */
public final class RuleModelWire {

    private RuleModelWire() {
    }

    /** 从独立快照 JSON（模型管理面的草稿/发布形态）构建并发布级校验。 */
    public static RuleModel fromSnapshot(com.fasterxml.jackson.databind.node.ObjectNode snapshot) {
        RuleModel.Builder builder = RuleModel.builder();
        if (snapshot == null) {
            return builder.build();
        }
        Map<String, Object> tree = toStrictMap(snapshot);
        Map<String, Object> fields = asMap(tree.get("fields"));
        Map<String, Object> rights = fields.get("rights") == null
                ? new java.util.LinkedHashMap<String, Object>()
                : asMap(fields.get("rights"));
        for (Map.Entry<String, Object> entry : rights.entrySet()) {
            builder.right(toField(entry.getKey(), asMap(entry.getValue()), null));
        }
        Map<String, Object> facts = fields.get("facts") == null
                ? new java.util.LinkedHashMap<String, Object>()
                : asMap(fields.get("facts"));
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            Map<String, Object> raw = asMap(entry.getValue());
            Object sourceValue = raw.get("source");
            FieldDefinition.FactSource source = sourceValue == null ? null
                    : FieldDefinition.FactSource.valueOf(String.valueOf(sourceValue));
            builder.fact(toField(entry.getKey(), raw, source));
        }
        Map<String, Object> actions = asMap(tree.get("actions"));
        for (Map.Entry<String, Object> entry : actions.entrySet()) {
            builder.action(entry.getKey(), toCondition(asMap(entry.getValue())));
        }
        RuleModel model = builder.build();
        RuleModelValidator.validate(model);
        return model;
    }

    /** Jackson 树→严格 Map（节点须为对象；数组保序转 List，标量原样）。 */
    private static Map<String, Object> toStrictMap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        try {
            return StrictJson.mapper().convertValue(node,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {
                    });
        } catch (RuntimeException failure) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    /** 从载荷 model 快照构建 RuleModel（null 快照返回空模型——无规则动作的载荷）。 */
    public static RuleModel fromSnapshot(io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload payload) {
        Map<String, Object> snapshot = payload.getModelSnapshot();
        RuleModel.Builder builder = RuleModel.builder();
        if (snapshot == null) {
            return builder.build();
        }
        Map<String, Object> fields = asMap(snapshot.get("fields"));
        Map<String, Object> rights = fields.get("rights") == null
                ? new java.util.LinkedHashMap<String, Object>()
                : asMap(fields.get("rights"));
        Map<String, Object> facts = fields.get("facts") == null
                ? new java.util.LinkedHashMap<String, Object>()
                : asMap(fields.get("facts"));
        for (Map.Entry<String, Object> entry : rights.entrySet()) {
            builder.right(toField(entry.getKey(), asMap(entry.getValue()), null));
        }
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            Map<String, Object> raw = asMap(entry.getValue());
            FieldDefinition.FactSource source = raw.get("source") == null ? null
                    : FieldDefinition.FactSource.valueOf(String.valueOf(raw.get("source")));
            builder.fact(toField(entry.getKey(), raw, source));
        }
        Map<String, Object> actions = asMap(snapshot.get("actions"));
        for (Map.Entry<String, Object> entry : actions.entrySet()) {
            builder.action(entry.getKey(), toCondition(asMap(entry.getValue())));
        }
        RuleModel model = builder.build();
        // 快照来自外部签名文件，装载即强制发布级校验：畸形/超预算/坏引用的树不能直达求值器
        io.github.surezzzzzz.sdk.license.core.model.RuleModelValidator.validate(model);
        return model;
    }

    private static FieldDefinition toField(String name, Map<String, Object> raw,
                                           FieldDefinition.FactSource source) {
        FieldType type = FieldType.fromCode(String.valueOf(raw.get("type")));
        FieldDefinition.Builder builder = FieldDefinition.builder(name, type);
        if (Boolean.TRUE.equals(raw.get("required"))) {
            builder.required();
        }
        if (raw.containsKey("minimum") || raw.containsKey("maximum")) {
            if (!(raw.get("minimum") instanceof Number) || !(raw.get("maximum") instanceof Number)) {
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            builder.range(ProtocolValueHelper.integer(raw.get("minimum")),
                    ProtocolValueHelper.integer(raw.get("maximum")));
        }
        if (raw.get("unit") != null) {
            builder.unit(String.valueOf(raw.get("unit")));
        }
        if (raw.containsKey("maxItems")) {
            builder.maxItems(ProtocolValueHelper.integer(raw.get("maxItems")));
        }
        if (raw.containsKey("maxLength")) {
            builder.maxLengthCodePoints(ProtocolValueHelper.integer(raw.get("maxLength")));
        }
        Object enumValues = raw.get("enumValues");
        if (enumValues instanceof List) {
            Set<String> values = new HashSet<String>();
            for (Object item : (List<?>) enumValues) {
                values.add(String.valueOf(item));
            }
            builder.enumValues(values);
        }
        if (source != null) {
            builder.source(source);
        }
        return builder.build();
    }

    private static ConditionNode toCondition(Map<String, Object> raw) {
        // RULES.1：每个对象恰好有一种节点键，多节点键/未知键确定拒绝（不按声明顺序取第一个）
        int presentKeys = 0;
        for (String key : raw.keySet()) {
            if (NODE_KEYS.contains(key)) {
                presentKeys++;
            } else {
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
            }
        }
        if (presentKeys != 1) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        // bool
        if (raw.get("bool") != null) {
            if (!(raw.get("bool") instanceof Boolean)) {
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            return ConditionNode.bool((Boolean) raw.get("bool"));
        }
        // all/any/not
        if (raw.get("all") != null) {
            return ConditionNode.all(toChildren(raw.get("all")));
        }
        if (raw.get("any") != null) {
            return ConditionNode.any(toChildren(raw.get("any")));
        }
        if (raw.get("not") != null) {
            return ConditionNode.not(toCondition(asMap(raw.get("not"))));
        }
        // compare
        if (raw.get("compare") != null) {
            Map<String, Object> compare = asMap(raw.get("compare"));
            requireExactKeys(compare, "operator", "left", "right");
            return ConditionNode.compare(
                    CompareOperator.fromCode(String.valueOf(compare.get("operator"))),
                    toOperand(compare.get("left")), toOperand(compare.get("right")));
        }
        // member
        Map<String, Object> member = asMap(raw.get("member"));
        requireExactKeys(member, "value", "set");
        return ConditionNode.member(toOperand(member.get("value")), toOperand(member.get("set")));
    }

    private static ConditionNode[] toChildren(Object raw) {
        if (!(raw instanceof List)) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        List<ConditionNode> children = new ArrayList<ConditionNode>();
        for (Object item : (List<?>) raw) {
            children.add(toCondition(asMap(item)));
        }
        return children.toArray(new ConditionNode[0]);
    }

    private static Operand toOperand(Object raw) {
        if (!(raw instanceof Map)) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        // 操作数恰为 ref 或 literal 之一，多余键拒绝
        if (map.containsKey("ref") && map.containsKey("literal")) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        if (map.size() != 1) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        Object ref = map.get("ref");
        if (ref instanceof String) {
            String[] parts = ((String) ref).split("\\.", 2);
            if (parts.length != 2) {
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            return Operand.ref(Operand.Namespace.fromCode(parts[0]), parts[1]);
        }
        Object literal = map.get("literal");
        if (literal instanceof Boolean) {
            return Operand.literal((Boolean) literal);
        }
        if (literal instanceof Number) {
            // 仅接受 JSON 安全整数；浮点或超范围值由统一入口拒绝，不截断。
            return Operand.literal(ProtocolValueHelper.integer(literal));
        }
        if (literal instanceof String) {
            return Operand.literal((String) literal);
        }
        if (literal instanceof List) {
            // STRING_SET 字面量：元素必须全为文本（集合比较按成员精确匹配，顺序无关）
            List<String> values = new ArrayList<String>();
            for (Object item : (List<?>) literal) {
                if (!(item instanceof String)) {
                    throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
                }
                values.add((String) item);
            }
            return Operand.literal(values);
        }
        throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
    }

    /** 条件节点键白名单（RULES.1 六节点）。 */
    private static final Set<String> NODE_KEYS = new HashSet<String>(java.util.Arrays.asList(
            "bool", "all", "any", "not", "compare", "member"));

    private static void requireExactKeys(Map<String, Object> map, String... allowedKeys) {
        Set<String> allowed = new HashSet<String>(java.util.Arrays.asList(allowedKeys));
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        return (Map<String, Object>) value;
    }
}
