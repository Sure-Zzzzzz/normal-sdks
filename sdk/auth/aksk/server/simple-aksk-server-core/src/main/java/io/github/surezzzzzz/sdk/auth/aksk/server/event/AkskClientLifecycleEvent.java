package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * OWNER_INHERITED AKU 的已脱敏生命周期事件。
 *
 * <p>事件不携带 secret、Token、权限集合或 DATA，仅用于提交后的审计和受控观测。</p>
 */
@Getter
public class AkskClientLifecycleEvent extends ApplicationEvent {

    private final AkskClientLifecycleEventType eventType;
    private final Instant eventTime;
    private final String clientId;
    private final String ownerSourceId;
    private final String ownerSubjectId;
    private final Long targetApplicationId;
    private final Long lifecycleVersion;

    public AkskClientLifecycleEvent(Object source, AkskClientLifecycleEventType eventType,
                                    String clientId, String ownerSourceId, String ownerSubjectId,
                                    Long targetApplicationId, Long lifecycleVersion) {
        super(source);
        this.eventType = eventType;
        this.eventTime = Instant.now();
        this.clientId = clientId;
        this.ownerSourceId = ownerSourceId;
        this.ownerSubjectId = ownerSubjectId;
        this.targetApplicationId = targetApplicationId;
        this.lifecycleVersion = lifecycleVersion;
    }
}
