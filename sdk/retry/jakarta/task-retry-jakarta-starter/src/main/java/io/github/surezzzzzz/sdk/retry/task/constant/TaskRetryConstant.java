package io.github.surezzzzzz.sdk.retry.task.constant;

/**
 * Task Retry 常量
 *
 * @author surezzzzzz
 */
public class TaskRetryConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.retry.task";
    public static final String PROPERTY_ENABLE = "enable";
    public static final String PROPERTY_TRUE = "true";

    public static final boolean DEFAULT_ENABLE = true;

    public static final int DEFAULT_RETRY_TIMES = 5;
    public static final long DEFAULT_INITIAL_DELAY_MILLIS = 5000L;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 1.5D;
    public static final long DEFAULT_MAX_DELAY_MILLIS = 30000L;

    public static final int FAST_RETRY_TIMES = 5;
    public static final long FAST_INITIAL_DELAY_MILLIS = 2000L;
    public static final double FAST_BACKOFF_MULTIPLIER = 1.2D;
    public static final long FAST_MAX_DELAY_MILLIS = 10000L;

    public static final int SLOW_RETRY_TIMES = 5;
    public static final long SLOW_INITIAL_DELAY_MILLIS = 10000L;
    public static final double SLOW_BACKOFF_MULTIPLIER = 2.0D;
    public static final long SLOW_MAX_DELAY_MILLIS = 60000L;

    private TaskRetryConstant() {
    }
}
