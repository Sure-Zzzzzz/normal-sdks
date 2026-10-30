package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布级模型校验：引用命中、类型/单位匹配、次序运算符仅整数、深度与节点预算。
 *
 * <p>发布（及签发侧权益整体校验）时执行一次；求值器运行期不再重复结构校验。</p>
 *
 * @author surezzzzzz
 */
public final class RuleModelValidator {

    private RuleModelValidator() {
    }

    /** 校验模型；违规抛出携带规则错误分类的异常。 */
    public static void validate(RuleModel model) {
        if (model == null) {
            throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        Set<String> units = new HashSet<String>();
        collectUnits(model.getRights(), units);
        collectUnits(model.getFacts(), units);
        Map<String, Integer> nodesPerAction = new HashMap<String, Integer>();
        for (Map.Entry<String, ConditionNode> action : model.getActions().entrySet()) {
            int count = walk(action.getValue(), model, 0, units);
            if (count > SmartLicenseCoreConstant.NODES_PER_ACTION_MAX) {
                throw new IllegalArgumentException(ErrorMessage.RULE_BUDGET_EXCEEDED);
            }
            nodesPerAction.put(action.getKey(), count);
        }
        int total = 0;
        for (Integer count : nodesPerAction.values()) {
            total += count;
        }
        if (total > SmartLicenseCoreConstant.MODEL_NODES_MAX) {
            throw new IllegalArgumentException(ErrorMessage.RULE_BUDGET_EXCEEDED);
        }
    }

    private static int walk(ConditionNode node, RuleModel model, int depth, Set<String> units) {
        // 根节点为第 1 层（depth 从 1 计）：最多 16 层即 depth>=17 拒绝（修正原 off-by-one）
        if (depth + 1 > SmartLicenseCoreConstant.CONDITION_DEPTH_MAX && depth > 0) {
            throw new IllegalArgumentException(ErrorMessage.RULE_BUDGET_EXCEEDED);
        }
        switch (node.getKind()) {
            case BOOL:
                return 1;
            case NOT:
            case ALL:
            case ANY: {
                int count = 1;
                for (ConditionNode child : ((ConditionNode.Logic) node).getChildren()) {
                    count += walk(child, model, depth + 1, units);
                }
                return count;
            }
            case COMPARE: {
                ConditionNode.Compare compare = (ConditionNode.Compare) node;
                validateOperand(compare.getLeft(), model);
                validateOperand(compare.getRight(), model);
                FieldType leftType = resolveType(compare.getLeft(), model);
                FieldType rightType = resolveType(compare.getRight(), model);
                if (leftType != rightType) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
                }
                if (compare.getOperator().isOrderOperator() && leftType != FieldType.INTEGER) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
                }
                checkUnits(compare.getLeft(), compare.getRight(), model, units);
                return 1;
            }
            case MEMBER: {
                ConditionNode.Member member = (ConditionNode.Member) node;
                validateOperand(member.getValue(), model);
                validateOperand(member.getSet(), model);
                FieldType valueType = resolveType(member.getValue(), model);
                FieldType setType = resolveType(member.getSet(), model);
                if (valueType != FieldType.STRING || setType != FieldType.STRING_SET) {
                    throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
                }
                return 1;
            }
            default:
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    private static void validateOperand(Operand operand, RuleModel model) {
        if (!operand.isRef()) {
            return;
        }
        Operand.FieldRef ref = operand.asRef();
        Map<String, FieldDefinition> definitions =
                ref.getNamespace() == Operand.Namespace.RIGHTS ? model.getRights() : model.getFacts();
        if (!definitions.containsKey(ref.getFieldName())) {
            throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    private static FieldType resolveType(Operand operand, RuleModel model) {
        if (!operand.isRef()) {
            return operand.asLiteral().getType();
        }
        Operand.FieldRef ref = operand.asRef();
        Map<String, FieldDefinition> definitions =
                ref.getNamespace() == Operand.Namespace.RIGHTS ? model.getRights() : model.getFacts();
        return definitions.get(ref.getFieldName()).getType();
    }

    private static void checkUnits(Operand left, Operand right, RuleModel model, Set<String> units) {
        String leftUnit = unitOf(left, model);
        String rightUnit = unitOf(right, model);
        boolean bothDeclared = leftUnit != null && rightUnit != null;
        if (bothDeclared && !leftUnit.equals(rightUnit)) {
            throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    private static String unitOf(Operand operand, RuleModel model) {
        if (!operand.isRef()) {
            return null;
        }
        Operand.FieldRef ref = operand.asRef();
        Map<String, FieldDefinition> definitions =
                ref.getNamespace() == Operand.Namespace.RIGHTS ? model.getRights() : model.getFacts();
        return definitions.get(ref.getFieldName()).getUnit();
    }

    private static void collectUnits(Map<String, FieldDefinition> definitions, Set<String> units) {
        for (FieldDefinition definition : definitions.values()) {
            if (definition.getUnit() != null) {
                units.add(definition.getUnit());
            }
        }
    }

    @SuppressWarnings("unused")
    private static void unused(List<String> ignore) {
        // 保持 Java 8 编译器对 List 引用的完整性，无运行时作用。
    }
}
