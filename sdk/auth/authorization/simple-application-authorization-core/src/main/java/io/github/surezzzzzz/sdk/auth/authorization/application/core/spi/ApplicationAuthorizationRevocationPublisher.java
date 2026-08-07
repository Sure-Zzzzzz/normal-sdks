package io.github.surezzzzzz.sdk.auth.authorization.application.core.spi;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationRevokedEvent;

/**
 * 应用授权撤销事件发布器。
 *
 * @author surezzzzzz
 */
public interface ApplicationAuthorizationRevocationPublisher {

    /**
     * 发布已发生的应用授权撤销事件。
     *
     * @param event 撤销事件
     */
    void publish(ApplicationAuthorizationRevokedEvent event);
}
