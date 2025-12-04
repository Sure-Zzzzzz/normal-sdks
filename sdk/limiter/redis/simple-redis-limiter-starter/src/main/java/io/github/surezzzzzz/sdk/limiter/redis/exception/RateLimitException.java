package io.github.surezzzzzz.sdk.limiter.redis.exception;

/**
 * 限流异常
 * 当请求被限流时抛出此异常
 *
 * @author: Sure.
 */
public class RateLimitException extends RedisLimiterException {

    /**
     * 限流结果码
     * 0 - 令牌不足
     * 2 - 请求重复（已存在于 Set 中）
     */
    private final int resultCode;

    /**
     * 构造方法：仅提供错误消息
     *
     * @param message 错误消息
     */
    public RateLimitException(String message) {
        super(message);
        this.resultCode = -1;
    }

    /**
     * 构造方法：提供错误消息和结果码
     *
     * @param message 错误消息
     * @param resultCode 限流结果码（0=令牌不足, 2=请求重复）
     */
    public RateLimitException(String message, int resultCode) {
        super(message);
        this.resultCode = resultCode;
    }

    /**
     * 获取限流结果码
     *
     * @return 结果码（0=令牌不足, 2=请求重复, -1=未知）
     */
    public int getResultCode() {
        return resultCode;
    }

    /**
     * 判断是否是去重失败（重复请求）
     *
     * @return true=重复请求, false=其他原因
     */
    public boolean isDuplicate() {
        return resultCode == 2;
    }

    /**
     * 判断是否是令牌不足
     *
     * @return true=令牌不足, false=其他原因
     */
    public boolean isInsufficientTokens() {
        return resultCode == 0;
    }

    /**
     * 获取友好的错误类型描述
     *
     * @return 错误类型描述
     */
    public String getErrorType() {
        if (isDuplicate()) {
            return "DUPLICATE_REQUEST";
        } else if (isInsufficientTokens()) {
            return "INSUFFICIENT_TOKENS";
        } else {
            return "UNKNOWN";
        }
    }
}
