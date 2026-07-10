package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider;

/**
 * ES Persistence 审计用户信息提供器
 *
 * @author surezzzzzz
 */
public interface EsPersistenceAuditUserProvider {

    /**
     * 获取客户端 ID
     *
     * @return 客户端 ID
     */
    String getClientId();

    /**
     * 获取客户端类型
     *
     * @return 客户端类型
     */
    String getClientType();

    /**
     * 获取用户 ID
     *
     * @return 用户 ID
     */
    String getUserId();

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    String getUsername();
}
