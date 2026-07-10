package io.github.surezzzzzz.sdk.retry.task.constant;

/**
 * Task Retry 错误信息
 *
 * @author surezzzzzz
 */
public class ErrorMessage {

    public static final String TASK_REQUIRED = "重试任务不能为空";
    public static final String REQUEST_REQUIRED = "重试请求不能为空";
    public static final String RETRY_TIMES_NEGATIVE = "重试次数不能为负数";
    public static final String INITIAL_DELAY_NEGATIVE = "初始延迟毫秒数不能为负数";
    public static final String MAX_DELAY_NEGATIVE = "最大延迟毫秒数不能为负数";
    public static final String MAX_DELAY_LESS_THAN_INITIAL_DELAY = "最大延迟毫秒数不能小于初始延迟毫秒数";
    public static final String BACKOFF_MULTIPLIER_INVALID = "退避倍数不能小于1";
    public static final String STRATEGY_TYPE_REQUIRED = "重试策略类型不能为空";

    private ErrorMessage() {
    }
}
