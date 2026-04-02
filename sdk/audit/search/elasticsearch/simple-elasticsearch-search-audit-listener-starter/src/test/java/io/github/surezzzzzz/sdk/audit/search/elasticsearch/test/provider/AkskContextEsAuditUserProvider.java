package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * EsAuditUserProvider implementation based on AkskAccessEvent + ThreadLocal.
 *
 * <p>Listens to {@link AkskAccessEvent} synchronously (same request thread as auth filter),
 * stores user info in ThreadLocal, and reads it when ES audit needs user context.
 *
 * <p>Requires {@link AkskContextClearInterceptor} to be registered to clean up
 * ThreadLocal after each request, preventing memory leaks in thread pool environments.
 *
 * @author surezzzzzz
 * @see AkskContextHolder
 * @see AkskContextClearInterceptor
 */
@Component
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "provider-type",
        havingValue = "aksk-event"
)
@Slf4j
public class AkskContextEsAuditUserProvider implements EsAuditUserProvider {

    @EventListener
    @Order(1)
    public void onAkskAccessEvent(AkskAccessEvent event) {
        AkskContextHolder.set(event);
        log.debug("AkskAccessEvent stored in ThreadLocal: clientId={}, source={}",
                event.getClientId(), event.getSource());
    }

    @Override
    public String getClientId() {
        AkskAccessEvent event = AkskContextHolder.get();
        return event != null ? event.getClientId() : null;
    }

    @Override
    public String getClientType() {
        AkskAccessEvent event = AkskContextHolder.get();
        return event != null ? event.getClientType() : null;
    }

    @Override
    public String getUserId() {
        AkskAccessEvent event = AkskContextHolder.get();
        return event != null ? event.getUserId() : null;
    }

    @Override
    public String getUsername() {
        AkskAccessEvent event = AkskContextHolder.get();
        return event != null ? event.getUsername() : null;
    }
}
