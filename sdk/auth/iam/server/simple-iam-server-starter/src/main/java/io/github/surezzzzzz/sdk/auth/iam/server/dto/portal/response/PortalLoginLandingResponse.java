package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 管理面读取的 Portal 全局登录首页。
 */
@Getter
@Builder
public class PortalLoginLandingResponse {

    private final String applicationCode;
    private final Long version;
}
