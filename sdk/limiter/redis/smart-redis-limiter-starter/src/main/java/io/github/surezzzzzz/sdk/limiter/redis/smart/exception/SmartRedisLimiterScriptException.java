package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import lombok.Getter;

/**
 * SmartRedisLimiter 脚本异常
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimiterScriptException extends SmartRedisLimiterException {

    private static final long serialVersionUID = 1L;

    /**
     * 路由 Key
     */
    private final String routeKey;

    /**
     * Redis datasource key
     */
    private final String datasourceKey;

    /**
     * Redis 模式
     */
    private final String redisMode;

    /**
     * 是否要求通过 redis-route 执行
     */
    private final boolean routeRequired;

    /**
     * 是否成功解析到 datasource
     */
    private final boolean routeResolved;

    public SmartRedisLimiterScriptException(String errorCode, String message) {
        this(errorCode, message, null, null, null, null, false, false);
    }

    public SmartRedisLimiterScriptException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, null, null, null, false, false);
    }

    public SmartRedisLimiterScriptException(String errorCode, String message, Throwable cause,
                                            String routeKey, String datasourceKey, String redisMode,
                                            boolean routeRequired, boolean routeResolved) {
        super(errorCode, message, cause);
        this.routeKey = routeKey;
        this.datasourceKey = datasourceKey;
        this.redisMode = redisMode;
        this.routeRequired = routeRequired;
        this.routeResolved = routeResolved;
    }
}
