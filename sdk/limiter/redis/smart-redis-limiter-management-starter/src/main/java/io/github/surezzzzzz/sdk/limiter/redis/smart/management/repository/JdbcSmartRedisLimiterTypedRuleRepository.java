package io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleLimitEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
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
 * 基于 Spring JDBC 的 v2 类型化规则 Repository
 *
 * <p>身份不适用字段以空串入库（数据库列 NOT NULL DEFAULT ''），
 * 保证七字段唯一索引对 DEFAULT 规则（object_id 空串）等形态可判重。
 *
 * @author surezzzzzz
 */
public class JdbcSmartRedisLimiterTypedRuleRepository implements SmartRedisLimiterTypedRuleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<SmartRedisLimiterTypedRuleEntity> ruleRowMapper = new TypedRuleRowMapper();
    private final RowMapper<SmartRedisLimiterTypedRuleLimitEntity> limitRowMapper = new TypedLimitRowMapper();

    /**
     * 构造 JDBC Repository
     *
     * @param jdbcTemplate 命名参数 JDBC 模板
     */
    public JdbcSmartRedisLimiterTypedRuleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Override
    public SmartRedisLimiterTypedRuleEntity findById(long id) {
        return findById(id, SmartRedisLimiterPolicyDataScope.all());
    }

    @Override
    public SmartRedisLimiterTypedRuleEntity findById(long id, SmartRedisLimiterPolicyDataScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                SmartRedisLimiterManagementConstant.PARAM_ID, id);
        List<SmartRedisLimiterTypedRuleEntity> rows = jdbcTemplate.query(
                scopedSql(SmartRedisLimiterManagementConstant.SQL_SELECT_TYPED_RULE_BY_ID, parameters, scope),
                parameters,
                ruleRowMapper);
        return loadLimits(rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public SmartRedisLimiterTypedRuleEntity findByIdentity(String serviceCode,
                                                           String resourceCode,
                                                           String dimension,
                                                           String selector,
                                                           String namespace,
                                                           String customType,
                                                           String objectId) {
        List<SmartRedisLimiterTypedRuleEntity> rows = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_TYPED_RULE_BY_KEY,
                new MapSqlParameterSource()
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_RESOURCE_CODE, resourceCode)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_DIMENSION, dimension)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_SELECTOR, selector)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_NAMESPACE, namespace)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_CUSTOM_TYPE, customType)
                        .addValue(SmartRedisLimiterManagementConstant.PARAM_OBJECT_ID, objectId),
                ruleRowMapper);
        return loadLimits(rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public long insert(SmartRedisLimiterTypedRuleEntity entity) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_INSERT_TYPED_RULE,
                    ruleParameters(entity), keyHolder,
                    new String[]{SmartRedisLimiterManagementConstant.COLUMN_ID});
            Number key = keyHolder.getKey();
            if (key == null) {
                throw persistenceException(null);
            }
            long ruleId = key.longValue();
            insertLimits(ruleId, entity.getLimits(), entity.getCreatedAt());
            return ruleId;
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw persistenceException(ex);
        }
    }

    @Override
    public boolean replaceLimits(long id, long expectedRowVersion,
                                 List<SmartRedisLimiterLimit> limits, Instant updatedAt,
                                 SmartRedisLimiterPolicyDataScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(SmartRedisLimiterManagementConstant.PARAM_ID, id)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_EXPECTED_ROW_VERSION, expectedRowVersion)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_UPDATED_AT, Timestamp.from(updatedAt));
        int updated = jdbcTemplate.update(scopedSql(
                SmartRedisLimiterManagementConstant.SQL_UPDATE_TYPED_RULE_VERSION, parameters, scope), parameters);
        if (updated == 0) {
            return false;
        }
        jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_DELETE_TYPED_LIMITS,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_RULE_ID, id));
        insertLimits(id, limits, updatedAt);
        return true;
    }

    @Override
    public boolean updateEnabled(long id, long expectedRowVersion, boolean enabled, Instant updatedAt,
                                 SmartRedisLimiterPolicyDataScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(SmartRedisLimiterManagementConstant.PARAM_ID, id)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_EXPECTED_ROW_VERSION, expectedRowVersion)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_ENABLED,
                        enabled ? SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_TRUE
                                : SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_FALSE)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_UPDATED_AT, Timestamp.from(updatedAt));
        int updated = jdbcTemplate.update(scopedSql(
                SmartRedisLimiterManagementConstant.SQL_UPDATE_TYPED_RULE_STATE, parameters, scope), parameters);
        return updated > 0;
    }

    @Override
    public boolean delete(long id, long expectedRowVersion, SmartRedisLimiterPolicyDataScope scope) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(SmartRedisLimiterManagementConstant.PARAM_ID, id)
                .addValue(SmartRedisLimiterManagementConstant.PARAM_EXPECTED_ROW_VERSION, expectedRowVersion);
        int deleted = jdbcTemplate.update(scopedSql(
                SmartRedisLimiterManagementConstant.SQL_DELETE_TYPED_RULE, parameters, scope), parameters);
        return deleted > 0;
    }

    @Override
    public List<SmartRedisLimiterTypedRuleEntity> findEnabledByServiceCode(String serviceCode) {
        List<SmartRedisLimiterTypedRuleEntity> rules = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_ENABLED_TYPED_RULES,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, serviceCode),
                ruleRowMapper);
        loadLimits(rules);
        return rules;
    }

    @Override
    public List<SmartRedisLimiterTypedRuleEntity> query(SmartRedisLimiterTypedRuleQuery query,
                                                        SmartRedisLimiterPolicyDataScope scope) {
        QuerySql querySql = buildQuery(query, false, scope);
        List<SmartRedisLimiterTypedRuleEntity> rules = jdbcTemplate.query(
                querySql.sql, querySql.parameters, ruleRowMapper);
        loadLimits(rules);
        return rules;
    }

    @Override
    public long count(SmartRedisLimiterTypedRuleQuery query, SmartRedisLimiterPolicyDataScope scope) {
        QuerySql querySql = buildQuery(query, true, scope);
        Long result = jdbcTemplate.queryForObject(querySql.sql, querySql.parameters, Long.class);
        return result == null ? 0L : result;
    }

    private SmartRedisLimiterTypedRuleEntity loadLimits(SmartRedisLimiterTypedRuleEntity rule) {
        if (rule == null) {
            return null;
        }
        rule.setLimits(jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_TYPED_LIMITS_BY_RULE_ID,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_RULE_ID, rule.getId()),
                limitRowMapper));
        return rule;
    }

    private void loadLimits(List<SmartRedisLimiterTypedRuleEntity> rules) {
        if (rules.isEmpty()) {
            return;
        }
        List<Long> ids = rules.stream()
                .map(SmartRedisLimiterTypedRuleEntity::getId)
                .collect(Collectors.toList());
        List<SmartRedisLimiterTypedRuleLimitEntity> limits = jdbcTemplate.query(
                SmartRedisLimiterManagementConstant.SQL_SELECT_TYPED_LIMITS_BY_RULE_IDS,
                new MapSqlParameterSource(SmartRedisLimiterManagementConstant.PARAM_RULE_IDS, ids),
                limitRowMapper);
        Map<Long, List<SmartRedisLimiterTypedRuleLimitEntity>> grouped = new HashMap<>();
        for (SmartRedisLimiterTypedRuleLimitEntity limit : limits) {
            grouped.computeIfAbsent(limit.getRuleId(), ignored -> new ArrayList<>()).add(limit);
        }
        for (SmartRedisLimiterTypedRuleEntity rule : rules) {
            rule.setLimits(grouped.getOrDefault(rule.getId(), Collections.emptyList()));
        }
    }

    private void insertLimits(long ruleId, List<? extends Object> sourceLimits, Instant timestamp) {
        int index = 0;
        for (Object source : sourceLimits) {
            SmartRedisLimiterLimit limit;
            if (source instanceof SmartRedisLimiterLimit) {
                limit = (SmartRedisLimiterLimit) source;
            } else {
                SmartRedisLimiterTypedRuleLimitEntity entity = (SmartRedisLimiterTypedRuleLimitEntity) source;
                limit = new SmartRedisLimiterLimit(
                        entity.getCount(), entity.getWindow(),
                        SmartRedisLimiterTimeUnit.fromCode(entity.getUnit()));
            }
            jdbcTemplate.update(SmartRedisLimiterManagementConstant.SQL_INSERT_TYPED_LIMIT,
                    new MapSqlParameterSource()
                            .addValue(SmartRedisLimiterManagementConstant.PARAM_RULE_ID, ruleId)
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

    private MapSqlParameterSource ruleParameters(SmartRedisLimiterTypedRuleEntity entity) {
        return new MapSqlParameterSource()
                .addValue(SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE, entity.getServiceCode())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_RESOURCE_CODE, entity.getResourceCode())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_DIMENSION, entity.getDimension())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_SELECTOR, entity.getSelector())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_NAMESPACE, entity.getNamespace())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_CUSTOM_TYPE, entity.getCustomType())
                .addValue(SmartRedisLimiterManagementConstant.PARAM_OBJECT_ID, entity.getObjectId())
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

    private QuerySql buildQuery(SmartRedisLimiterTypedRuleQuery query, boolean count,
                                SmartRedisLimiterPolicyDataScope scope) {
        StringBuilder sql = new StringBuilder(count
                ? SmartRedisLimiterManagementConstant.SQL_COUNT_TYPED_RULE_BASE
                : SmartRedisLimiterManagementConstant.SQL_QUERY_TYPED_RULE_BASE);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        sql = new StringBuilder(scopedSql(sql.toString(), parameters, scope));
        appendTextCondition(sql, parameters, query.getServiceCode(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_SERVICE_CODE,
                SmartRedisLimiterManagementConstant.PARAM_SERVICE_CODE);
        appendTextCondition(sql, parameters, query.getResourceCode(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_RESOURCE_CODE,
                SmartRedisLimiterManagementConstant.PARAM_RESOURCE_CODE);
        if (query.getDimension() != null && !query.getDimension().trim().isEmpty()) {
            List<String> dimensions = new ArrayList<>();
            for (String part : query.getDimension().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    dimensions.add(trimmed);
                }
            }
            if (!dimensions.isEmpty()) {
                sql.append(SmartRedisLimiterManagementConstant.SQL_CONDITION_DIMENSION);
                parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_DIMENSIONS, dimensions);
            }
        }
        appendTextCondition(sql, parameters, query.getSelector(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_SELECTOR,
                SmartRedisLimiterManagementConstant.PARAM_SELECTOR);
        appendTextCondition(sql, parameters, query.getNamespace(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_NAMESPACE,
                SmartRedisLimiterManagementConstant.PARAM_NAMESPACE);
        appendTextCondition(sql, parameters, query.getCustomType(),
                SmartRedisLimiterManagementConstant.SQL_CONDITION_CUSTOM_TYPE,
                SmartRedisLimiterManagementConstant.PARAM_CUSTOM_TYPE);
        String objectId = query.getObjectId();
        if (objectId != null && !objectId.trim().isEmpty()) {
            // LIKE 前缀匹配：以 | 为转义符按字面量检索（| 自身也需转义），通配 % 由参数侧拼接
            sql.append(SmartRedisLimiterManagementConstant.SQL_CONDITION_TYPED_OBJECT);
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_OBJECT_ID,
                    objectId.trim().replace("|", "||").replace("%", "|%").replace("_", "|_") + "%");
        }
        if (query.getEnabled() != null) {
            sql.append(SmartRedisLimiterManagementConstant.SQL_CONDITION_ENABLED);
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_ENABLED,
                    query.getEnabled() ? SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_TRUE
                            : SmartRedisLimiterManagementConstant.DATABASE_BOOLEAN_FALSE);
        }
        if (!count) {
            sql.append(SmartRedisLimiterManagementConstant.SQL_TYPED_RULE_PAGE_ORDER);
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_LIMIT, query.getSize());
            parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_OFFSET,
                    (long) (query.getPage() - 1) * query.getSize());
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

    private String scopedSql(String sql, MapSqlParameterSource parameters,
                             SmartRedisLimiterPolicyDataScope scope) {
        if (scope == null) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        scope.requireUsable();
        if (scope.isAll()) {
            return sql;
        }
        parameters.addValue(SmartRedisLimiterManagementConstant.PARAM_ALLOWED_SERVICE_CODES,
                scope.getServiceCodes());
        return sql + SmartRedisLimiterManagementConstant.SQL_CONDITION_DATA_SCOPE;
    }

    private SmartRedisLimiterManagementException persistenceException(Throwable cause) {
        return new SmartRedisLimiterManagementException(
                ErrorCode.PERSISTENCE_FAILED, ErrorMessage.PERSISTENCE_FAILED, cause);
    }

    private static final class TypedRuleRowMapper implements RowMapper<SmartRedisLimiterTypedRuleEntity> {
        @Override
        public SmartRedisLimiterTypedRuleEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            SmartRedisLimiterTypedRuleEntity entity = new SmartRedisLimiterTypedRuleEntity();
            entity.setId(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_ID));
            entity.setServiceCode(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_SERVICE_CODE));
            entity.setResourceCode(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_RESOURCE_CODE));
            entity.setDimension(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_DIMENSION));
            entity.setSelector(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_SELECTOR));
            entity.setNamespace(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_NAMESPACE));
            entity.setCustomType(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_CUSTOM_TYPE));
            entity.setObjectId(resultSet.getString(SmartRedisLimiterManagementConstant.COLUMN_OBJECT_ID));
            entity.setEnabled(resultSet.getBoolean(SmartRedisLimiterManagementConstant.COLUMN_ENABLED));
            entity.setRowVersion(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_ROW_VERSION));
            entity.setCreatedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_CREATED_AT));
            entity.setUpdatedAt(instant(resultSet, SmartRedisLimiterManagementConstant.COLUMN_UPDATED_AT));
            return entity;
        }
    }

    private static final class TypedLimitRowMapper implements RowMapper<SmartRedisLimiterTypedRuleLimitEntity> {
        @Override
        public SmartRedisLimiterTypedRuleLimitEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            SmartRedisLimiterTypedRuleLimitEntity entity = new SmartRedisLimiterTypedRuleLimitEntity();
            entity.setId(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_ID));
            entity.setRuleId(resultSet.getLong(SmartRedisLimiterManagementConstant.COLUMN_RULE_ID));
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

    private static final class QuerySql {
        private final String sql;
        private final MapSqlParameterSource parameters;

        private QuerySql(String sql, MapSqlParameterSource parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }
    }
}
