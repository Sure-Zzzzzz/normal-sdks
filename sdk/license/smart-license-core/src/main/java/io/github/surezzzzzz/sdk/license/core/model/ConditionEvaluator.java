package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.List;

/**
 * license-condition/1 求值器：类型树执行 + 预算单位计数。
 *
 * <p>缺失、类型不符与超预算为确定拒绝（{@link LicenseCoreException}，错误分类
 * RULE_FACT_MISSING/RULE_BUDGET），不作为 false 参与逻辑运算、不能被 not 翻转。</p>
 *
 * @author surezzzzzz
 */
public final class ConditionEvaluator {

    private final RuleModel model;
    private int budgetUsed;

    private ConditionEvaluator(RuleModel model) {
        this.model = model;
    }

    /** 对给定动作执行规则；动作未声明或规则拒绝语义见异常。 */
    public static boolean evaluate(RuleModel model, String action, FactValues values) {
        if (model == null || action == null || values == null) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        ConditionNode root = model.getActions().get(action);
        if (root == null) {
            throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
        ConditionEvaluator evaluator = new ConditionEvaluator(model);
        // 设计§编译与求值顺序第2条：先计算本动作全部引用字段并核对存在，跨 any/not 不短路省略
        evaluator.verifyReferencedPresent(root, values);
        return evaluator.run(root, values);
    }

    private void verifyReferencedPresent(ConditionNode node, FactValues values) {
        switch (node.getKind()) {
            case BOOL:
                return;
            case NOT:
            case ALL:
            case ANY:
                for (ConditionNode child : ((ConditionNode.Logic) node).getChildren()) {
                    verifyReferencedPresent(child, values);
                }
                return;
            case COMPARE:
                verifyOperand(((ConditionNode.Compare) node).getLeft(), values);
                verifyOperand(((ConditionNode.Compare) node).getRight(), values);
                return;
            case MEMBER:
                verifyOperand(((ConditionNode.Member) node).getValue(), values);
                verifyOperand(((ConditionNode.Member) node).getSet(), values);
                return;
            default:
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    private void verifyOperand(Operand operand, FactValues values) {
        if (!operand.isRef()) {
            return;
        }
        Operand.FieldRef ref = operand.asRef();
        if (!values.has(ref.getNamespace(), ref.getFieldName())) {
            throw new FactValues.MissingFactException(ErrorMessage.RULE_FACT_MISSING);
        }
    }

    private boolean run(ConditionNode node, FactValues values) {
        spend(1);
        switch (node.getKind()) {
            case BOOL:
                return ((ConditionNode.Constant) node).getValue();
            case NOT:
                return !run(((ConditionNode.Logic) node).getChildren().get(0), values);
            case ALL: {
                for (ConditionNode child : ((ConditionNode.Logic) node).getChildren()) {
                    if (!run(child, values)) {
                        return false;
                    }
                }
                return true;
            }
            case ANY: {
                for (ConditionNode child : ((ConditionNode.Logic) node).getChildren()) {
                    if (run(child, values)) {
                        return true;
                    }
                }
                return false;
            }
            case COMPARE:
                return compare((ConditionNode.Compare) node, values);
            case MEMBER:
                return member((ConditionNode.Member) node, values);
            default:
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    private boolean compare(ConditionNode.Compare node, FactValues values) {
        Object left = resolve(node.getLeft(), values);
        Object right = resolve(node.getRight(), values);
        switch (node.getOperator()) {
            case EQ:
                return left.equals(right);
            case NE:
                return !left.equals(right);
            case LT:
                return ((Long) left) < ((Long) right);
            case LTE:
                return ((Long) left) <= ((Long) right);
            case GT:
                return ((Long) left) > ((Long) right);
            case GTE:
                return ((Long) left) >= ((Long) right);
            default:
                throw new LicenseCoreException(ErrorCode.RULE_STRUCTURE, ErrorMessage.RULE_STRUCTURE_INVALID);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean member(ConditionNode.Member node, FactValues values) {
        String value = (String) resolve(node.getValue(), values);
        List<String> set = (List<String>) resolve(node.getSet(), values);
        spend(set.size());
        return set.contains(value);
    }

    private Object resolve(Operand operand, FactValues values) {
        if (!operand.isRef()) {
            return operand.asLiteral().getValue();
        }
        Operand.FieldRef ref = operand.asRef();
        return values.read(ref.getNamespace(), ref.getFieldName());
    }

    private void spend(int units) {
        budgetUsed += units;
        if (budgetUsed > SmartLicenseCoreConstant.EVALUATION_BUDGET_UNITS) {
            throw new LicenseCoreException(ErrorCode.RULE_BUDGET, ErrorMessage.RULE_BUDGET_EXCEEDED);
        }
    }
}
