package io.github.surezzzzzz.sdk.auth.authorization.application.core.spi;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationRevokedEvent;

/**
 * 应用授权撤销事件处理器。
 *
 * @author surezzzzzz
 */
public interface ApplicationAuthorizationRevocationHandler {

    /**
     * 处理已发生的应用授权撤销事件。
     *
     * @param event 撤销事件
     */
    void handle(ApplicationAuthorizationRevokedEvent event);
}
