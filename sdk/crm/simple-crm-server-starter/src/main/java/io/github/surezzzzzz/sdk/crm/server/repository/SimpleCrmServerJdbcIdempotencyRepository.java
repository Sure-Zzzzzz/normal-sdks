package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerIdempotencyEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * 命令幂等 JDBC 仓储：唯一键 scope_key 收敛并发首请求；摘要冲突显式拒绝。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcIdempotencyRepository extends SimpleCrmServerJdbcRepositorySupport {

    private static final RowMapper<SimpleCrmServerIdempotencyEntity> MAPPER = (rs, rowNum) -> new SimpleCrmServerIdempotencyEntity(
            rs.getString("scope_key"), rs.getString("request_digest"),
            rs.getString("first_resource_id"), rs.getString("first_result_json"),
            rs.getTimestamp("created_at").toInstant());

    /**
     * 创建幂等仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcIdempotencyRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /**
     * 读取当前幂等事实，避免事务中较早建立的快照掩盖并发首执行。
     *
     * @param scopeKey 稳定范围键
     * @return 当前幂等记录
     */
    public Optional<SimpleCrmServerIdempotencyEntity> findCurrent(String scopeKey) {
        return queryOne("SELECT scope_key, request_digest, first_resource_id, first_result_json,"
                        + " created_at FROM crm_idempotency WHERE scope_key = :scopeKey FOR UPDATE",
                new MapSqlParameterSource("scopeKey", scopeKey), MAPPER);
    }

    /**
     * 在业务回调之前占用幂等范围。唯一键冲突会等待首事务提交或回滚；
     * 同一业务事务回滚时占位也随之消失。
     *
     * @param entity 尚未保存首结果的占位记录
     * @return 本事务是否取得首次执行权
     */
    public boolean claim(SimpleCrmServerIdempotencyEntity entity) {
        try {
            int inserted = jdbc.update("INSERT INTO crm_idempotency (scope_key, request_digest,"
                            + " first_resource_id, first_result_json, created_at) VALUES (:scopeKey, :digest,"
                            + " NULL, NULL, :createdAt)",
                    new MapSqlParameterSource("scopeKey", entity.getScopeKey())
                            .addValue("digest", entity.getRequestDigest())
                            .addValue("createdAt", Timestamp.from(entity.getCreatedAt())));
            if (inserted != 1) {
                throw new IllegalStateException(SimpleCrmServerConstant.ERROR_IDEMPOTENCY_CLAIM_COUNT);
            }
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    /**
     * 完成创建命令的首结果；必须只更新本事务的占位行。
     *
     * @param scopeKey 稳定范围键
     * @param requestDigest 请求摘要
     * @param resourceId 首次创建的资源标识
     */
    public void completeCreate(String scopeKey, String requestDigest, String resourceId) {
        int updated = jdbc.update("UPDATE crm_idempotency SET first_resource_id = :resourceId"
                        + " WHERE scope_key = :scopeKey AND request_digest = :digest"
                        + " AND first_resource_id IS NULL AND first_result_json IS NULL",
                new MapSqlParameterSource("scopeKey", scopeKey).addValue("digest", requestDigest)
                        .addValue("resourceId", resourceId));
        if (updated != 1) {
            throw new IllegalStateException(SimpleCrmServerConstant.ERROR_IDEMPOTENCY_CREATE_RESULT);
        }
    }

    /**
     * 完成已知目标命令的首结果；JSON 字符串 {@code null} 与 SQL NULL 占位不同。
     *
     * @param scopeKey 稳定范围键
     * @param requestDigest 请求摘要
     * @param resultJson 首次成功结果 JSON
     */
    public void completeResult(String scopeKey, String requestDigest, String resultJson) {
        int updated = jdbc.update("UPDATE crm_idempotency SET first_result_json = :resultJson"
                        + " WHERE scope_key = :scopeKey AND request_digest = :digest"
                        + " AND first_resource_id IS NULL AND first_result_json IS NULL",
                new MapSqlParameterSource("scopeKey", scopeKey).addValue("digest", requestDigest)
                        .addValue("resultJson", resultJson));
        if (updated != 1) {
            throw new IllegalStateException(SimpleCrmServerConstant.ERROR_IDEMPOTENCY_RESULT);
        }
    }
}
