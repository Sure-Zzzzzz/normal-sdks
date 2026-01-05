package io.github.surezzzzzz.sdk.expression.condition.parser.exception;

/**
 * 表达式验证异常
 * <p>
 * 当表达式不满足验证规则时抛出，如深度超限、条件数超限等。
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
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
        return String.format("表达式验证失败：%s为 %d，超过允许的最大值 %d",
                metricType.getDescription(), actualValue, maxValue);
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public int getActualValue() {
        return actualValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    /**
     * 度量类型枚举
     */
    public enum MetricType {
        /**
         * 表达式深度
         */
        DEPTH("深度"),

        /**
         * 条件数量
         */
        CONDITION_COUNT("条件数");

        private final String description;

        MetricType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
