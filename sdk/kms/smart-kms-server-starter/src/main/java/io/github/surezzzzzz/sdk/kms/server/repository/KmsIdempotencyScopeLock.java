package io.github.surezzzzzz.sdk.kms.server.repository;

/**
 * 管理幂等作用域的跨实例互斥锁。
 *
 * @author surezzzzzz
 */
public interface KmsIdempotencyScopeLock {

    /**
     * 在有限时间内取得指定摘要作用域的互斥锁。
     *
     * @param scopeHash 不可逆作用域摘要
     * @return 成功取得锁时为 true
     */
    boolean tryLock(String scopeHash);

    /**
     * 释放当前会话持有的指定摘要作用域锁。
     *
     * @param scopeHash 不可逆作用域摘要
     */
    void unlock(String scopeHash);
}
