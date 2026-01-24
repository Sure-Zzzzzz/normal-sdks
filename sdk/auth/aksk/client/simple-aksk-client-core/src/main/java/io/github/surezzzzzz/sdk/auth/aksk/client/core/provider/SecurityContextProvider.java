package io.github.surezzzzzz.sdk.auth.aksk.client.core.provider;

/**
 * Security Context Provider
 * <p>
 * 应用层实现此接口，提供当前请求的安全上下文
 *
 * @author surezzzzzz
 */
public interface SecurityContextProvider {

    /**
     * 获取当前请求的安全上下文（JSON 格式）
     *
     * @return 安全上下文 JSON 字符串，如: {"user_id":"10086","tenant_id":"tenant-123"}
     *         如果返回 null，则不传递 security_context 参数
     */
    String getSecurityContext();
}
