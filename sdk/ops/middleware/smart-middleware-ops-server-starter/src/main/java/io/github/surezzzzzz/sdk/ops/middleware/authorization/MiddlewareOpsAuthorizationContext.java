package io.github.surezzzzzz.sdk.ops.middleware.authorization;

import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentity;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import lombok.Builder;
import lombok.Getter;

/**
 * 运维能力授权上下文。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsAuthorizationContext {

    /**
     * 已认证主体。
     */
    private final MiddlewareOpsIdentity identity;
    /**
     * 固定只读能力。
     */
    private final MiddlewareOpsCapability capability;
    /**
     * 中间件类型。
     */
    private final MiddlewareType middlewareType;
    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 资源范围安全投影。
     */
    private final String resourceScope;
}
