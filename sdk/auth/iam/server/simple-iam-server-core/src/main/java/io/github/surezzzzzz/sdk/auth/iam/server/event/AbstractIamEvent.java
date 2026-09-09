package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * IAM 全部审计事件的公共根。
 *
 * <p>统一根使未来审计消费者可经单一 {@code @EventListener} 入口订阅 IAM 全部事件族
 * （Token / 认证 / 会话 / 管理面）；各族保持自身字段语义，不互相耦合。
 *
 * @author surezzzzzz
 */
@Getter
public abstract class AbstractIamEvent extends ApplicationEvent {

    /**
     * 事件对象创建时间，不等同于业务对象（Token / 会话等）的签发时间。
     */
    private final Instant eventTime;

    protected AbstractIamEvent(Object source) {
        super(source);
        this.eventTime = Instant.now();
    }
}
