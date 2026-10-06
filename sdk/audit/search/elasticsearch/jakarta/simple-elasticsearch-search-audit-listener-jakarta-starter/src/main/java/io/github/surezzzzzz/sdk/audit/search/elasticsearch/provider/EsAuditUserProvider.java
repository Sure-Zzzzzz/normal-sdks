package io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider;

/**
 * 审计主体信息提供器
 *
 * @author surezzzzzz
 */
public interface EsAuditUserProvider {

    /**
     * 获取客户端标识
     */
    String getClientId();

    /**
     * 获取客户端类型
     */
    String getClientType();

    /**
     * 获取用户标识
     */
    String getUserId();

    /**
     * 获取用户名
     */
    String getUsername();
}
