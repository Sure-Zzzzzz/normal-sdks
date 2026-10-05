package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.ExpressionVisitor;

/**
 * 表达式抽象基类
 * 所有条件表达式节点的基类，使用访问者模式支持多种转换
 *
 * @author surezzzzzz
 */
public abstract class Expression {

    /**
     * 接受访问者访问（访问者模式）
     * 业务层可实现自定义 Visitor 将 AST 转换为 SQL、ES DSL、MongoDB 查询等
     *
     * @param visitor 访问者
     * @param <R>     返回值类型
     * @return 访问结果
     */
    public abstract <R> R accept(ExpressionVisitor<R> visitor);
}
