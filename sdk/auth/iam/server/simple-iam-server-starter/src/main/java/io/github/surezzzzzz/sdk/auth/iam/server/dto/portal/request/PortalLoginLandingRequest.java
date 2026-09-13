package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

/**
 * Portal 无深链登录首页单例写请求。
 */
@Data
public class PortalLoginLandingRequest {

    /**
     * 应用编码；null 表示明确关闭全局登录首页。
     */
    private String applicationCode;
    private Long version;
}
