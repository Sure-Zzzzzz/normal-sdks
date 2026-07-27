package io.github.surezzzzzz.sdk.kms.server.service;

/**
 * tenant 内逻辑密钥事务锁端口。
 *
 * @author surezzzzzz
 */
public interface KmsKeyLock {

    /**
     * 锁定当前事务中的 tenant 内逻辑密钥。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 密钥存在并已锁定时返回 {@code true}
     */
    boolean lock(String tenantId, String keyRef);
}
