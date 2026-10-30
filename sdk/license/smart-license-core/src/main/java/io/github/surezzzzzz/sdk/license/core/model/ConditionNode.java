package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.util.Arrays;
import java.util.List;

/**
 * 条件树节点联合：all/any/not/bool/compare/member，每节点恰有一种键。
 *
 * <p>构造期校验子项数量与预算；求值期错误为确定拒绝，不作为 false 参与逻辑运算。</p>
 *
 * @author surezzzzzz
 */
public abstract class ConditionNode {

    private ConditionNode() {
    }

    /** 全部子条件为 true。 */
    public static ConditionNode all(ConditionNode... children) {
        return new Logic(LogicKind.ALL, java.util.Collections.unmodifiableList(new java.util.ArrayList<ConditionNode>(java.util.Arrays.asList(children))));
    }

    /** 至少一项子条件为 true。 */
    public static ConditionNode any(ConditionNode... children) {
        return new Logic(LogicKind.ANY, java.util.Collections.unmodifiableList(new java.util.ArrayList<ConditionNode>(java.util.Arrays.asList(children))));
    }

    /** 布尔取反（单子节点）。 */
    public static ConditionNode not(ConditionNode child) {
        return new Logic(LogicKind.NOT, Arrays.asList(child));
    }

    /** 明确无额外约束的布尔常量，不能绕过基础校验。 */
    public static ConditionNode bool(boolean value) {
        return new Constant(value);
    }

    /** 类型严格比较：EQ/NE 同型，LT/LTE/GT/GTE 仅整数；集合不支持比较。 */
    public static ConditionNode compare(CompareOperator operator, Operand left, Operand right) {
        return new Compare(operator, left, right);
    }

    /** 文本是否属于文本集合（value/集合均可为引用或字面量）。 */
    public static ConditionNode member(Operand value, Operand set) {
        return new Member(value, set);
    }

    /** 节点种类。 */
    public enum Kind {
        ALL, ANY, NOT, BOOL, COMPARE, MEMBER
    }

    public abstract Kind getKind();

    /** 逻辑节点。 */
    static final class Logic extends ConditionNode {

        private final LogicKind kind;
        private final List<ConditionNode> children;

        private Logic(LogicKind kind, List<ConditionNode> children) {
            int expected = kind == LogicKind.NOT ? 1 : -1;
            if (children == null || children.isEmpty()
                    || children.contains(null)
                    || (expected > 0 && children.size() != expected)
                    || children.size() > SmartLicenseCoreConstant.CHILDREN_PER_LOGIC_NODE_MAX) {
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            this.kind = kind;
            this.children = children;
        }

        @Override
        public Kind getKind() {
            return kind == LogicKind.NOT ? Kind.NOT : (kind == LogicKind.ALL ? Kind.ALL : Kind.ANY);
        }

        List<ConditionNode> getChildren() {
            return children;
        }

        LogicKind getLogicKind() {
            return kind;
        }
    }

    enum LogicKind {
        ALL, ANY, NOT
    }

    /** 布尔常量节点。 */
    static final class Constant extends ConditionNode {

        private final boolean value;

        private Constant(boolean value) {
            this.value = value;
        }

        @Override
        public Kind getKind() {
            return Kind.BOOL;
        }

        boolean getValue() {
            return value;
        }
    }

    /** 比较节点。 */
    static final class Compare extends ConditionNode {

        private final CompareOperator operator;
        private final Operand left;
        private final Operand right;

        private Compare(CompareOperator operator, Operand left, Operand right) {
            if (operator == null || left == null || right == null) {
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        public Kind getKind() {
            return Kind.COMPARE;
        }

        CompareOperator getOperator() {
            return operator;
        }

        Operand getLeft() {
            return left;
        }

        Operand getRight() {
            return right;
        }
    }

    /** 集合成员节点。 */
    static final class Member extends ConditionNode {

        private final Operand value;
        private final Operand set;

        private Member(Operand value, Operand set) {
            if (value == null || set == null) {
                throw new IllegalArgumentException(ErrorMessage.RULE_STRUCTURE_INVALID);
            }
            this.value = value;
            this.set = set;
        }

        @Override
        public Kind getKind() {
            return Kind.MEMBER;
        }

        Operand getValue() {
            return value;
        }

        Operand getSet() {
            return set;
        }
    }
}
