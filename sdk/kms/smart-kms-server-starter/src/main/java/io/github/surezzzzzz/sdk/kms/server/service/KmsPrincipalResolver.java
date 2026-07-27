package io.github.surezzzzzz.sdk.kms.server.service;

import javax.servlet.http.HttpServletRequest;

/**
 * 已认证 HTTP 请求的 KMS 主体解析端口。
 *
 * <p>实现只能从已验证的认证上下文派生主体、tenant、scope 与 requestId，不能信任请求业务字段。
 * 没有可用实现时，KMS 敏感 HTTP API 不得启动。</p>
 *
 * @author surezzzzzz
 */
public interface KmsPrincipalResolver {

    /**
     * 解析已认证请求的 KMS 上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 已认证 KMS 请求上下文
     */
    KmsRequestContext resolve(HttpServletRequest request);
}
