package io.github.surezzzzzz.sdk.expression.condition.parser.support;

import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import java.util.*;

/** 有界字段和值收集；结果只读，值节点本身仍为调用方持有的可变对象。 */
public final class ExpressionCollectors {
    private ExpressionCollectors() { }
    /** 按首次出现顺序去重；空根返回空集合，非法树拒绝。 */
    public static Set<String> collectFields(Expression expr) {
        Set<String> fields = new LinkedHashSet<>();
        ExpressionTreeHelper.walk(expr, (node, depth) -> {
            String field = ExpressionTreeHelper.fieldOf(node);
            if (field != null) fields.add(field);
        });
        return Collections.unmodifiableSet(fields);
    }
    /** 按树顺序保留重复值；EXISTS/NULL 检查没有值，不添加伪 null。 */
    public static List<ValueNode> collectValues(Expression expr) {
        List<ValueNode> values = new ArrayList<>();
        ExpressionTreeHelper.walk(expr, (node, depth) -> {
            if (node instanceof ComparisonExpression) values.add(((ComparisonExpression) node).getValue());
            else if (node instanceof InExpression) values.addAll(((InExpression) node).getValues());
            else if (node instanceof LikeExpression && ((LikeExpression) node).getValue() != null)
                values.add(((LikeExpression) node).getValue());
        });
        return Collections.unmodifiableList(values);
    }
}
