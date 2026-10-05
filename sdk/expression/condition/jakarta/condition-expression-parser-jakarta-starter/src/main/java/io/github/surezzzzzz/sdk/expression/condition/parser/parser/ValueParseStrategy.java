package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;

/**
 * 值解析策略接口（策略模式）
 * <p>
 * 不同的值类型有不同的解析策略，通过优先级决定解析顺序
 * <p>
 * 策略执行顺序（按优先级从高到低）：
 * <ol>
 *   <li>BooleanValueParseStrategy - 布尔值（优先级 1）</li>
 *   <li>TimeRangeValueParseStrategy - 时间范围（优先级 2）</li>
 *   <li>NumberValueParseStrategy - 数字（优先级 3）</li>
 *   <li>StringValueParseStrategy - 字符串（优先级 99，兜底）</li>
 * </ol>
 *
 * @author surezzzzzz
 */
public interface ValueParseStrategy {

    /**
     * 判断是否可以解析该值
     *
     * @param rawValue 原始值字符串
     * @return true 如果可以解析
     */
    boolean canParse(String rawValue);

    /**
     * 解析值
     *
     * @param rawValue 原始值字符串
     * @return 解析后的值节点
     */
    ValueNode parse(String rawValue);

    /**
     * 获取优先级（数字越小越优先）
     *
     * @return 优先级
     */
    int getPriority();
}
