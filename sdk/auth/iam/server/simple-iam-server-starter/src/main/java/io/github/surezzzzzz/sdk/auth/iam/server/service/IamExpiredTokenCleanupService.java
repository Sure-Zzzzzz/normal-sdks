package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRefreshTokenFamilyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * 过期 Token 物理清理服务。
 *
 * <p>清理对象与判定口径：</p>
 * <ul>
 *   <li>{@code iam_refresh_token_family}：{@code expires_at} 已过的族（过期即无
 *       防御价值，物理删行；未过期的已吊销族保留供追溯）</li>
 *   <li>{@code oauth2_authorization}：行内全部 token 均已死亡的授权行，按
 *       refresh → access → code 的寿命优先级三路判定（有 refresh 的行以
 *       refresh 过期为准；无 refresh 看 access；两者皆无看未消费的授权码）</li>
 * </ul>
 *
 * <p>删除一律分批执行、每批独立事务，避免大事务长锁表；只记日志，
 * 不发事件不进审计表。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
public class IamExpiredTokenCleanupService {

    private static final String SQL_DELETE_EXPIRED_AUTHORIZATION_BY_REFRESH =
            "DELETE FROM oauth2_authorization WHERE refresh_token_expires_at < ? LIMIT ?";
    private static final String SQL_DELETE_EXPIRED_AUTHORIZATION_BY_ACCESS =
            "DELETE FROM oauth2_authorization WHERE refresh_token_expires_at IS NULL "
                    + "AND access_token_expires_at < ? LIMIT ?";
    private static final String SQL_DELETE_EXPIRED_AUTHORIZATION_BY_CODE =
            "DELETE FROM oauth2_authorization WHERE refresh_token_expires_at IS NULL "
                    + "AND access_token_expires_at IS NULL AND authorization_code_expires_at < ? LIMIT ?";

    private final IamRefreshTokenFamilyRepository refreshTokenFamilyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SimpleIamServerProperties properties;
    private final TransactionTemplate transactionTemplate;

    public IamExpiredTokenCleanupService(IamRefreshTokenFamilyRepository refreshTokenFamilyRepository,
                                         JdbcTemplate jdbcTemplate,
                                         SimpleIamServerProperties properties,
                                         PlatformTransactionManager transactionManager) {
        this.refreshTokenFamilyRepository = refreshTokenFamilyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 清理全部过期 token 数据（族表 + 授权表），返回删除总数。
     */
    public int cleanupExpired() {
        Instant now = Instant.now();
        int families = deleteFamiliesInBatches(now);
        int authorizations = deleteAuthorizationsInBatches(now);
        log.info("Expired token cleanup finished: {} refresh families deleted, {} authorizations deleted",
                families, authorizations);
        return families + authorizations;
    }

    /**
     * 分批物理删除过期族（每批独立事务防长事务锁表）
     */
    private int deleteFamiliesInBatches(Instant now) {
        int batchSize = properties.getCleanup().getBatchSize();
        int total = 0;
        int deleted;
        do {
            deleted = refreshTokenFamilyRepository.deleteExpiredBatch(now, batchSize);
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }

    /**
     * 三路分批删除死透的授权行（每批独立事务）
     */
    private int deleteAuthorizationsInBatches(Instant now) {
        int total = deleteInBatches(SQL_DELETE_EXPIRED_AUTHORIZATION_BY_REFRESH, now);
        total += deleteInBatches(SQL_DELETE_EXPIRED_AUTHORIZATION_BY_ACCESS, now);
        total += deleteInBatches(SQL_DELETE_EXPIRED_AUTHORIZATION_BY_CODE, now);
        return total;
    }

    private int deleteInBatches(String sql, Instant now) {
        int batchSize = properties.getCleanup().getBatchSize();
        int total = 0;
        int deleted;
        do {
            Integer batchDeleted = transactionTemplate.execute(
                    status -> jdbcTemplate.update(sql, now, batchSize));
            deleted = batchDeleted == null ? 0 : batchDeleted;
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
