package io.github.surezzzzzz.sdk.kms.server.service;

/**
 * owner 内逻辑密钥事务锁端口。
 *
 * @author surezzzzzz
 */
public interface KmsKeyLock {

    /**
     * 锁定当前事务中的 owner 内逻辑密钥。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @param keyRef           逻辑密钥标识
     * @return 密钥存在并已锁定时返回 {@code true}
     */
    boolean lock(String ownerPrincipalId, String keyRef);
}
