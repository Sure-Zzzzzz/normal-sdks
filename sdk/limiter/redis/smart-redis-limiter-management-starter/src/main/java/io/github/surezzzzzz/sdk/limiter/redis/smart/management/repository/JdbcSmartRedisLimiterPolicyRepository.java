package io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyLimitEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyRevisionEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 Spring JDBC 的限流策略 Repository
 *
 * @author surezzzzzz
 */
public class JdbcSmartRedisLimiterPolicyRepository implements SmartRedisLimiterPolicyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<SmartRedisLimiterPolicyEntity> policyRowMapper = new PolicyRowMapper();
    private final RowMapper<SmartRedisLimiterPolicyLimitEntity> limitRowMapper = new LimitRowMapper();
    private final RowMapper<SmartRedisLimiterPolicyRevisionEntity> revisionRowMapper = new RevisionRowMapper();

    /**
     * 构造 JDBC Repository
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcSmartRedisLimiterPolicyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void initializeRevision(String serviceCode) {
        jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_INITIALIZE_REVISION,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_PUBLISHED_AT,
                                Timestamp.from(Instant.EPOCH)));
    }

    @Override
    public SmartRedisLimiterPolicyRevisionEntity lockRevision(String serviceCode) {
        List<SmartRedisLimiterPolicyRevisionEntity> rows = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_REVISION_FOR_UPDATE,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode),
                revisionRowMapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public SmartRedisLimiterPolicyRevisionEntity findRevision(String serviceCode) {
        List<SmartRedisLimiterPolicyRevisionEntity> rows = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_REVISION,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode),
                revisionRowMapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void updateRevision(String serviceCode, long revision, Instant publishedAt) {
        jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_UPDATE_REVISION,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_REVISION, revision)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_PUBLISHED_AT,
                                Timestamp.from(publishedAt)));
    }

    @Override
    public SmartRedisLimiterPolicyEntity findById(long id) {
        List<SmartRedisLimiterPolicyEntity> rows = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_POLICY_BY_ID,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_ID, id),
                policyRowMapper);
        return loadLimits(rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public SmartRedisLimiterPolicyEntity findByIdentity(String serviceCode,
                                                        String resourceCode,
                                                        String subject) {
        List<SmartRedisLimiterPolicyEntity> rows = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_POLICY_BY_KEY,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_RESOURCE_CODE, resourceCode)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_SUBJECT, subject),
                policyRowMapper);
        return loadLimits(rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public long insert(SmartRedisLimiterPolicyEntity entity) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_INSERT_POLICY,
                    policyParameters(entity), keyHolder,
                    new String[]{SmartRedisLimiterManagementConstant.COLUMN_ID});
            Number key = keyHolder.getKey();
            if (key == null) {
                throw persistenceException(null);
            }
            long policyId = key.longValue();
            insertLimits(policyId, entity.getLimits(), entity.getCreatedAt());
            return policyId;
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw persistenceException(ex);
        }
    }

    @Override
    public boolean replaceLimits(long id, long expectedRowVersion,
                                 List<SmartRedisLimiterLimit> limits, Instant updatedAt) {
        int updated = jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_UPDATE_POLICY_VERSION,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_ID, id)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_EXPECTED_ROW_VERSION,
                                expectedRowVersion)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_UPDATED_AT,
                                Timestamp.from(updatedAt)));
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_DELETE_LIMITS,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_POLICY_ID, id));
        insertLimits(id, limits, updatedAt);
        return true;
    }

    @Override
    public boolean updateEnabled(long id, long expectedRowVersion, boolean enabled, Instant updatedAt) {
        int updated = jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_UPDATE_POLICY_STATE,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_ID, id)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_EXPECTED_ROW_VERSION,
                                expectedRowVersion)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_ENABLED,
                                enabled ? SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_TRUE
                                        : SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_FALSE)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_UPDATED_AT,
                                Timestamp.from(updatedAt)));
        return updated > 0;
    }

    @Override
    public boolean delete(long id, long expectedRowVersion) {
        int deleted = jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_DELETE_POLICY,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_ID, id)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_EXPECTED_ROW_VERSION,
                                expectedRowVersion));
        return deleted > 0;
    }

    @Override
    public List<SmartRedisLimiterPolicyEntity> findEnabledByServiceCode(String serviceCode) {
        List<SmartRedisLimiterPolicyEntity> policies = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_ENABLED_POLICIES,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode),
                policyRowMapper);
        loadLimits(policies);
        return policies;
    }

    @Override
    public List<SmartRedisLimiterPolicyEntity> query(SmartRedisLimiterPolicyQuery query) {
        QuerySql querySql = buildQuery(query, false);
        List<SmartRedisLimiterPolicyEntity> policies = jdbcTemplate.query(
                querySql.sql, querySql.parameters, policyRowMapper);
        loadLimits(policies);
        return policies;
    }

    @Override
    public long count(SmartRedisLimiterPolicyQuery query) {
        QuerySql querySql = buildQuery(query, true);
        Long result = jdbcTemplate.queryForObject(querySql.sql, querySql.parameters, Long.class);
        return result == null ? 0L : result;
    }

    private SmartRedisLimiterPolicyEntity loadLimits(SmartRedisLimiterPolicyEntity policy) {
        if (policy == null) {
            return null;
        }
        policy.setLimits(jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_LIMITS_BY_POLICY_ID,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_POLICY_ID, policy.getId()),
                limitRowMapper));
        return policy;
    }

    private void loadLimits(List<SmartRedisLimiterPolicyEntity> policies) {
        if (policies.isEmpty()) {
            return;
        }
        List<Long> ids = policies.stream()
                .map(SmartRedisLimiterPolicyEntity::getId)
                .collect(Collectors.toList());
        List<SmartRedisLimiterPolicyLimitEntity> limits = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_LIMITS_BY_POLICY_IDS,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_POLICY_IDS, ids),
                limitRowMapper);
        Map<Long, List<SmartRedisLimiterPolicyLimitEntity>> grouped = new HashMap<>();
        for (SmartRedisLimiterPolicyLimitEntity limit : limits) {
            grouped.computeIfAbsent(limit.getPolicyId(), ignored -> new ArrayList<>()).add(limit);
        }
        for (SmartRedisLimiterPolicyEntity policy : policies) {
            policy.setLimits(grouped.getOrDefault(policy.getId(), Collections.emptyList()));
        }
    }

    private void insertLimits(long policyId,
                              List<? extends Object> sourceLimits,
                              Instant timestamp) {
        int index = 0;
        for (Object source : sourceLimits) {
            SmartRedisLimiterLimit limit;
            if (source instanceof SmartRedisLimiterLimit) {
                limit = (SmartRedisLimiterLimit) source;
            } else {
                SmartRedisLimiterPolicyLimitEntity entity = (SmartRedisLimiterPolicyLimitEntity) source;
                limit = new SmartRedisLimiterLimit(
                        entity.getCount(), entity.getWindow(),
                        SmartRedisLimiterTimeUnit.fromCode(entity.getUnit()));
            }
            jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_INSERT_LIMIT,
                    new MapSqlParameterSource()
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_POLICY_ID, policyId)
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_SORT_ORDER, index++)
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_COUNT, limit.getCount())
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_WINDOW, limit.getWindow())
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_UNIT, limit.getUnit().getCode())
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_WINDOW_SECONDS,
                                    limit.getWindowSeconds())
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_CREATED_AT,
                                    Timestamp.from(timestamp))
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_UPDATED_AT,
                                    Timestamp.from(timestamp)));
        }
    }

    private MapSqlParameterSource policyParameters(SmartRedisLimiterPolicyEntity entity) {
        return new MapSqlParameterSource()
                .addValue(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, entity.getServiceCode())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_RESOURCE_CODE, entity.getResourceCode())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_SUBJECT, entity.getSubject())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_ENABLED,
                        Boolean.TRUE.equals(entity.getEnabled())
                                ? SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_TRUE
                                : SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_FALSE)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_ROW_VERSION, entity.getRowVersion())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_CREATED_AT,
                        Timestamp.from(entity.getCreatedAt()))
                .addValue(SmartRedisLimiterManagementConstant.PARAM_UPDATED_AT,
                        Timestamp.from(entity.getUpdatedAt()));
    }

    private QuerySql buildQuery(SmartRedisLimiterPolicyQuery query, boolean count) {
        StringBuilder sql = new StringBuilder(count
                ? SmartRedisLimiterManagementConstant.SQL_COUNT_POLICY_BASE
                : SmartRedisLimiterManagementConstant.SQL_QUERY_POLICY_BASE);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendTextCondition(sql, parameters, query.getServiceCode(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_SERVICE_CODE,
                SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE);
        appendTextCondition(sql, parameters, query.getResourceCode(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_RESOURCE_CODE,
                SmartRedisLimiterManagementConstant.PARAM_RESOURCE_CODE);
        appendTextCondition(sql, parameters, query.getSubject(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_SUBJECT,
                SmartRedisLimiterManagementConstant.PARAM_SUBJECT);
        if (query.getEnabled() != null) {
            sql.append(SmartRedisLimiterManagementConstant.SQL_CONDITION_ENABLED);
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_ENABLED,
                    query.getEnabled() ? SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_TRUE
                            : SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_FALSE);
        }
        if (!count) {
            sql.append(SmartRedisLimiterManagementConstant.SQL_POLICY_PAGE_ORDER);
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_LIMIT, query.getSize());
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_OFFSET,
                    (query.getPage() - 1) * query.getSize());
        }
        return new QuerySql(sql.toString(), parameters);
    }

    private void appendTextCondition(StringBuilder sql,
                                     MapSqlParameterSource parameters,
                                     String value,
                                     String condition,
                                     String parameter) {
        if (value != null && !value.trim().isEmpty()) {
            sql.append(condition);
            parameters.addValue(parameter, value.trim());
        }
    }

    private SmartRedisLimiterManagementException persistenceException(Throwable cause) {
        return new SmartRedisLimiterManagementException(
                ErrorCode.PERSISTENCE_FAILED, ErrorMessage.PERSISTENCE_FAILED, cause);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final class PolicyRowMapper implements RowMapper<SmartRedisLimiterPolicyEntity> {
        @Override
        public SmartRedisLimiterPolicyEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            SmartRedisLimiterPolicyEntity entity = new SmartRedisLimiterPolicyEntity();
            entity.setId(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_ID));
            entity.setServiceCode(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_SERVICE_CODE));
            entity.setResourceCode(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_RESOURCE_CODE));
            entity.setSubject(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_SUBJECT));
            entity.setEnabled(resultSet.getBoolean(SmartRedisLimiterManagementConstant.COLUMN_ENABLED));
            entity.setRowVersion(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_ROW_VERSION));
            entity.setCreatedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_CREATED_AT));
            entity.setUpdatedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_UPDATED_AT));
            return entity;
        }
    }

    private static final class LimitRowMapper implements RowMapper<SmartRedisLimiterPolicyLimitEntity> {
        @Override
        public SmartRedisLimiterPolicyLimitEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            SmartRedisLimiterPolicyLimitEntity entity = new SmartRedisLimiterPolicyLimitEntity();
            entity.setId(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_ID));
            entity.setPolicyId(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_POLICY_ID));
            entity.setSortOrder(resultSet.getInt(SmartRedisLimiterManagementConstant.COLUMN_SORT_ORDER));
            entity.setCount(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_LIMIT_COUNT));
            entity.setWindow(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_LIMIT_WINDOW));
            entity.setUnit(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_LIMIT_UNIT));
            entity.setWindowSeconds(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_WINDOW_SECONDS));
            entity.setCreatedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_CREATED_AT));
            entity.setUpdatedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_UPDATED_AT));
            return entity;
        }
    }

    private static final class RevisionRowMapper implements RowMapper<SmartRedisLimiterPolicyRevisionEntity> {
        @Override
        public SmartRedisLimiterPolicyRevisionEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            SmartRedisLimiterPolicyRevisionEntity entity = new SmartRedisLimiterPolicyRevisionEntity();
            entity.setServiceCode(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_SERVICE_CODE));
            entity.setRevision(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_REVISION));
            entity.setPublishedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_PUBLISHED_AT));
            return entity;
        }
    }

    private static final class QuerySql {
        private final String sql;
        private final MapSqlParameterSource parameters;

        private QuerySql(String sql, MapSqlParameterSource parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }
    }
}
