package io.github.surezzzzzz.sdk.limiter.redis.smart.event;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterTypedManagementEventPayload;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * SmartRedisLimiter 类型化动态策略管理事件
 * <p>仅对真实类型化规则变更发布；普通 Spring Event 仍非可靠审计队列，
 * 监听器异常不反写已提交事务。</p>
 *
 * @author surezzzzzz
 */
@Getter
public class SmartRedisLimiterTypedManagementEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 类型化管理事件载荷
     */
    private final SmartRedisLimiterTypedManagementEventPayload payload;

    /**
     * 构造类型化管理事件
     *
     * @param source  事件发布者
     * @param payload 类型化管理事件载荷
     */
    public SmartRedisLimiterTypedManagementEvent(Object source,
                                                 SmartRedisLimiterTypedManagementEventPayload payload) {
        super(source);
        if (payload == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.TYPED_EVENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.TYPED_EVENT_PAYLOAD_INVALID,
                            ErrorMessage.REASON_TYPED_EVENT_PAYLOAD_REQUIRED));
        }
        this.payload = payload;
    }
}
