package io.github.surezzzzzz.sdk.expression.condition.parser.visitor;

import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;

/**
 * 表达式访问者接口（访问者模式）
 * <p>
 * 业务层可实现此接口，将 AST 转换为各种目标格式：
 * <ul>
 *   <li>SQL 查询: {@code class SqlVisitor implements ExpressionVisitor<String>}</li>
 *   <li>Elasticsearch DSL: {@code class EsVisitor implements ExpressionVisitor<QueryBuilder>}</li>
 *   <li>MongoDB 查询: {@code class MongoVisitor implements ExpressionVisitor<Bson>}</li>
 *   <li>内存过滤: {@code class FilterVisitor implements ExpressionVisitor<Predicate<T>>}</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 解析表达式
 * Expression ast = parser.parse("类型='活跃' AND 分类 IN ('高','中')");
 *
 * // 转换为 SQL
 * SqlVisitor sqlVisitor = new SqlVisitor();
 * String sql = ast.accept(sqlVisitor);
 * // 结果: "type = ? AND category IN (?, ?)"
 *
 * // 转换为 Elasticsearch DSL
 * EsVisitor esVisitor = new EsVisitor();
 * QueryBuilder query = ast.accept(esVisitor);
 * }</pre>
 *
 * @param <R> 访问结果类型
 * @author surezzzzzz
 */
public interface ExpressionVisitor<R> {

    /**
     * 访问二元逻辑表达式（AND/OR）
     *
     * @param expr 二元表达式
     * @return 访问结果
     */
    R visitBinary(BinaryExpression expr);

    /**
     * 访问一元表达式（NOT）
     *
     * @param expr 一元表达式
     * @return 访问结果
     */
    R visitUnary(UnaryExpression expr);

    /**
     * 访问比较表达式（=, !=, >, <, >=, <=）
     *
     * @param expr 比较表达式
     * @return 访问结果
     */
    R visitComparison(ComparisonExpression expr);

    /**
     * 访问 IN 表达式（IN, NOT IN）
     *
     * @param expr IN 表达式
     * @return 访问结果
     */
    R visitIn(InExpression expr);

    /**
     * 访问 LIKE 表达式（LIKE, PREFIX, SUFFIX, NOT LIKE）
     *
     * @param expr LIKE 表达式
     * @return 访问结果
     */
    R visitLike(LikeExpression expr);

    /**
     * 访问 NULL 表达式（IS NULL, IS NOT NULL）
     *
     * @param expr NULL 表达式
     * @return 访问结果
     */
    R visitNull(NullExpression expr);

    /**
     * 访问括号分组表达式
     *
     * @param expr 括号表达式
     * @return 访问结果
     */
    R visitParenthesis(ParenthesisExpression expr);
}
