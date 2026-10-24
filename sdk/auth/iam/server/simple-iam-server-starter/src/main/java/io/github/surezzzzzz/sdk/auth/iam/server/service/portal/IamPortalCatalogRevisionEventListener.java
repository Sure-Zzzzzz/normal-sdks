package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 目录 revision 事件的提交后观测出口，不向人工审计或协作消费契约扩散。
 */
@Slf4j
@SimpleIamServerComponent
public class IamPortalCatalogRevisionEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRevisionBumped(IamPortalCatalogRevisionBumpedEvent event) {
        log.debug("eventCode=IAM_PORTAL_CATALOG_REVISION_BUMPED, eventId={}, "
                        + "Portal 可信应用路由投影 revision 变更已提交：before={}, after={}, changeType={}, "
                        + "applicationCodeDigest={}, correlationId={}",
                event.getEventId(), event.getBeforeRevision(), event.getAfterRevision(), event.getChangeType(),
                event.getApplicationCodeDigest(), event.getCorrelationId());
    }
}
