package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求值输入：权益值与本次运行事实（类型化严格包装）。
 *
 * <p>未声明字段拒绝、类型严格校验（Long/Boolean/String/List&lt;String&gt;），不做隐式默认值；
 * 构造期完成全部类型校验，求值器此后只处理缺失与预算。</p>
 *
 * @author surezzzzzz
 */
public final class FactValues {

    private final Map<String, Object> rights;
    private final Map<String, Object> facts;

    private FactValues(Map<String, Object> rights, Map<String, Object> facts) {
        this.rights = rights;
        this.facts = facts;
    }

    /**
     * 按模型校验并包装权益与事实。
     *
     * @param model 已声明模型
     * @param rights 权益值（可为 null 表示全部留空，签发期另行整体校验必填）
     * @param facts 本次事实（可为 null 表示未提供）
     * @param validateRequiredRights 是否执行 rights 必填整体校验（签发侧 true，求值侧只校验已提供值）
     */
    public static FactValues of(RuleModel model, Map<String, ?> rights, Map<String, ?> facts,
                                boolean validateRequiredRights) {
        return new FactValues(
                normalize(model.getRights(), rights, validateRequiredRights, Operand.Namespace.RIGHTS),
                normalize(model.getFacts(), facts, false, Operand.Namespace.FACTS));
    }

    /** 读取字段值；缺失抛缺失异常（不返回 null 默认）。 */
    public Object read(Operand.Namespace namespace, String fieldName) {
        Map<String, Object> source = namespace == Operand.Namespace.RIGHTS ? rights : facts;
        Object value = source.get(fieldName);
        if (value == null) {
            throw new MissingFactException(ErrorMessage.RULE_FACT_MISSING);
        }
        return value;
    }

    /** 是否存在该字段值。 */
    public boolean has(Operand.Namespace namespace, String fieldName) {
        return (namespace == Operand.Namespace.RIGHTS ? rights : facts).containsKey(fieldName);
    }

    private static Map<String, Object> normalize(Map<String, FieldDefinition> definitions, Map<String, ?> input,
                                                 boolean validateRequired, Operand.Namespace namespace) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (input != null) {
            for (Map.Entry<String, ?> entry : input.entrySet()) {
                FieldDefinition definition = definitions.get(entry.getKey());
                if (definition == null) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
                }
                result.put(entry.getKey(), checkValue(definition, entry.getValue()));
            }
        }
        if (validateRequired) {
            for (FieldDefinition definition : definitions.values()) {
                if (definition.isRequired() && !result.containsKey(definition.getName())) {
                    throw new IllegalArgumentException(ErrorMessage.PROTOCOL_FIELD_INVALID);
                }
            }
        }
        return result;
    }

    private static Object checkValue(FieldDefinition definition, Object value) {
        switch (definition.getType()) {
            case INTEGER:
                // Jackson 把小整数解析为 Integer、大整数为 Long；wire 往返的权益/事实必须两种都接受并
                // 归一化为 Long。显式拒绝浮点（Double/Float）——小数业务必须用缩放整数表达（RULES.1）。
                if (!(value instanceof Integer || value instanceof Long)) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                long number = ((Number) value).longValue();
                if (number < definition.getMinimum() || number > definition.getMaximum()) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                return number;
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                return value;
            case STRING:
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                String text = (String) value;
                if (definition.getMaxLengthCodePoints() != null
                        && text.codePointCount(0, text.length()) > definition.getMaxLengthCodePoints()) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                if (!definition.getEnumValues().isEmpty() && !definition.getEnumValues().contains(text)) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                return text;
            case STRING_SET:
                if (!(value instanceof List)) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                List<?> list = (List<?>) value;
                if (definition.getMaxItems() != null && list.size() > definition.getMaxItems()) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                }
                for (Object item : list) {
                    if (!(item instanceof String)) {
                        throw new IllegalArgumentException(ErrorMessage.RULE_FACT_MISSING);
                    }
                }
                // 先复制调用方集合，再暴露只读视图，避免校验后通过原集合改变判定输入。
                return Collections.unmodifiableList(new ArrayList<Object>(list));
            default:
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    /** 必需值缺失的确定求值异常：语义为拒绝，不作为 false 参与逻辑运算。 */
    public static final class MissingFactException extends RuntimeException {

        public MissingFactException(String message) {
            super(message);
        }
    }
}
