package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 基于当前 MySQL 会话命名锁的幂等作用域互斥锁。
 *
 * @author surezzzzzz
 */
public class JdbcKmsIdempotencyScopeLock implements KmsIdempotencyScopeLock {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建幂等作用域互斥锁。
     */
    public JdbcKmsIdempotencyScopeLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 限定只使用固定长度的十六进制摘要构造数据库锁名。
     */
    private static void validateScopeHash(String scopeHash) {
        if (scopeHash == null || scopeHash.length() != 64) {
            throw new KmsValidationException();
        }
        for (int index = 0; index < scopeHash.length(); index++) {
            char character = scopeHash.charAt(index);
            if ((character < '0' || character > '9') && (character < 'a' || character > 'f')) {
                throw new KmsValidationException();
            }
        }
    }

    /**
     * 尝试取得当前事务连接上的命名锁。
     */
    @Override
    public boolean tryLock(String scopeHash) {
        validateScopeHash(scopeHash);
        try {
            Number result = jdbcTemplate.queryForObject(SmartKmsServerConstant.SQL_TRY_LOCK_IDEMPOTENCY_SCOPE,
                    Number.class, scopeHash,
                    Integer.valueOf(SmartKmsServerConstant.IDEMPOTENCY_SCOPE_LOCK_TIMEOUT_SECONDS));
            if (result == null) {
                throw new KmsPersistenceException();
            }
            return result.intValue() == 1;
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 释放当前会话上的命名锁。
     */
    @Override
    public void unlock(String scopeHash) {
        validateScopeHash(scopeHash);
        try {
            Number result = jdbcTemplate.queryForObject(SmartKmsServerConstant.SQL_RELEASE_IDEMPOTENCY_SCOPE_LOCK,
                    Number.class, scopeHash);
            if (result == null || result.intValue() != 1) {
                throw new KmsPersistenceException();
            }
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }
}
