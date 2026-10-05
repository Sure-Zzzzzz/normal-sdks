package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 对外提示只列实际 parser 支持的语法，不把结构化专属操作符混入表达式。
 */
public final class ExpressionSyntaxConstant {
    public static final List<String> OPERATORS = Collections.unmodifiableList(Arrays.asList(
            "=", "!=", ">", ">=", "<", "<=", "IN", "NOT IN", "LIKE", "NOT LIKE", "PREFIX LIKE",
            "NOT PREFIX LIKE", "SUFFIX LIKE", "NOT SUFFIX LIKE", "EXISTS", "NOT EXISTS",
            "IS NULL", "IS NOT NULL", "AND", "OR", "NOT"));

    private ExpressionSyntaxConstant() {
    }
}
