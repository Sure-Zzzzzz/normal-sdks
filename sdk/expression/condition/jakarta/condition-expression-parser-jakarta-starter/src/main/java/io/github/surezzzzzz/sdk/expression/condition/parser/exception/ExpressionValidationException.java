package io.github.surezzzzzz.sdk.expression.condition.parser.exception;

import lombok.Getter;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage;

/**
 * 表达式验证异常
 * <p>
 * 当表达式不满足验证规则时抛出，如深度超限、条件数超限等。
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
@Getter
public class ExpressionValidationException extends RuntimeException {

    /**
     * 验证失败的度量类型
     */
    private final MetricType metricType;

    /**
     * 实际值
     */
    private final int actualValue;

    /**
     * 允许的最大值
     */
    private final int maxValue;

    /**
     * 构造验证异常
     *
     * @param metricType  度量类型
     * @param actualValue 实际值
     * @param maxValue    允许的最大值
     */
    public ExpressionValidationException(MetricType metricType, int actualValue, int maxValue) {
        super(buildMessage(metricType, actualValue, maxValue));
        this.metricType = metricType;
        this.actualValue = actualValue;
        this.maxValue = maxValue;
    }

    /**
     * 构造验证异常（带原因）
     *
     * @param metricType  度量类型
     * @param actualValue 实际值
     * @param maxValue    允许的最大值
     * @param cause       原因
     */
    public ExpressionValidationException(MetricType metricType, int actualValue, int maxValue, Throwable cause) {
        super(buildMessage(metricType, actualValue, maxValue), cause);
        this.metricType = metricType;
        this.actualValue = actualValue;
        this.maxValue = maxValue;
    }

    /**
     * 构建错误消息
     */
    private static String buildMessage(MetricType metricType, int actualValue, int maxValue) {
        if (metricType == MetricType.INVALID_TREE) return ErrorMessage.INVALID_TREE;
        return String.format(ErrorMessage.METRIC_ERROR,
                metricType.getDescription(), actualValue, maxValue);
    }

    /**
     * 度量类型枚举
     */
    @Getter
    public enum MetricType {
        /**
         * 表达式深度
         */
        DEPTH("深度"),

        /**
         * 条件数量
         */
        CONDITION_COUNT("条件数"),
        AST_DEPTH("树深度"),
        NODE_COUNT("节点数"),
        IN_VALUE_COUNT("IN值数"),
        INVALID_TREE("无效树结构");

        private final String description;

        MetricType(String description) {
            this.description = description;
        }

        /** 稳定英文度量码。 */
        public String getCode() { return name(); }
        /** 未知码返回 null。 */
        public static MetricType fromCode(String code) {
            for (MetricType value : values()) if (value.name().equals(code)) return value;
            return null;
        }
        /** 判断已知度量码。 */
        public static boolean isValid(String code) { return fromCode(code) != null; }
        /** 返回独立代码数组。 */
        public static String[] getAllCodes() {
            return java.util.Arrays.stream(values()).map(MetricType::getCode).toArray(String[]::new);
        }
    }
}
