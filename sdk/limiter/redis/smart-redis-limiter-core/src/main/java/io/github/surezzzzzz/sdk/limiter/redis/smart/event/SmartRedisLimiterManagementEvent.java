package io.github.surezzzzzz.sdk.limiter.redis.smart.event;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * SmartRedisLimiter 动态策略管理事件
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimiterManagementEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 管理事件载荷
     */
    private final SmartRedisLimiterManagementEventPayload payload;

    /**
     * 构造动态策略管理事件
     *
     * @param source  事件发布者
     * @param payload 管理事件载荷
     */
    public SmartRedisLimiterManagementEvent(Object source,
                                            SmartRedisLimiterManagementEventPayload payload) {
        super(source);
        if (payload == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID,
                            ErrorMessage.REASON_MANAGEMENT_PAYLOAD_REQUIRED));
        }
        this.payload = payload;
    }
}
