package io.github.surezzzzzz.sdk.ops.middleware.authentication;

import lombok.Builder;
import lombok.Getter;

/**
 * 已认证运维操作者的稳定安全投影。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsIdentity {

    /**
     * 稳定主体标识。
     */
    private final String subject;
    /**
     * 可选展示名。
     */
    private final String displayName;
    /**
     * 认证机制。
     */
    private final String authenticationMechanism;
}
