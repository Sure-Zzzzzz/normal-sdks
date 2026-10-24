package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * 直接执行状态行初始化，避免 Spring Data 为 MySQL upsert 派生查询。
 */
@Repository
public class IamApplicationAuthorizationStateRepositoryCustomImpl
        implements IamApplicationAuthorizationStateRepositoryCustom {

    private static final String INSERT_IF_ABSENT_SQL = "INSERT INTO iam_application_authorization_state " +
            "(application_id, authorization_epoch, owner_inherited_access_epoch, owner_inheritance_enabled, " +
            "recompute_barrier, barrier_version, created_at, updated_at) " +
            "VALUES (:applicationId, 1, 1, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
            "ON DUPLICATE KEY UPDATE application_id = VALUES(application_id)";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int insertIfAbsent(Long applicationId) {
        return entityManager.createNativeQuery(INSERT_IF_ABSENT_SQL)
                .setParameter("applicationId", applicationId)
                .executeUpdate();
    }
}
