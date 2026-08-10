package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;

import javax.servlet.http.HttpServletRequest;

/**
 * 资源服务认证编排引擎。
 *
 * @author surezzzzzz
 */
public interface ResourceServerEngine {

    /**
     * 对受保护请求执行唯一来源认证。
     *
     * @param request HTTP请求
     * @return 认证结果
     */
    ResourceAuthenticationResult authenticate(HttpServletRequest request);
}
