package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerCustomerEntity;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 客户档案 JDBC 仓储：按 owner 条件写实现 CAS 更新；keyset 游标按 id 升序。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcCustomerRepository extends SimpleCrmServerJdbcRepositorySupport implements CustomerRepository {

    private static final String COLUMNS =
            "customer_id, display_name, state, owner, merged_into_customer_id, aggregate_version,"
                    + " created_at, updated_at";

    private static final RowMapper<SimpleCrmServerCustomerEntity> MAPPER = (rs, rowNum) -> new SimpleCrmServerCustomerEntity(
            rs.getString("customer_id"), rs.getString("display_name"), rs.getString("state"),
            rs.getString("owner"), nullableString(rs, "merged_into_customer_id"),
            rs.getLong("aggregate_version"), instant(rs, "created_at"), instant(rs, "updated_at"));

    /**
     * 创建客户仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcCustomerRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        MapSqlParameterSource params = new MapSqlParameterSource("customerId", customerId);
        return queryOne("SELECT " + COLUMNS + " FROM crm_customer WHERE customer_id = :customerId",
                params, MAPPER).map(SimpleCrmServerCustomerEntity::toDomain);
    }

    @Override
    public Customer insert(Customer customer) {
        SimpleCrmServerCustomerEntity e = SimpleCrmServerCustomerEntity.from(customer);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", e.getCustomerId()).addValue("displayName", e.getDisplayName())
                .addValue("state", e.getStateCode()).addValue("owner", e.getOwner())
                .addValue("mergedInto", e.getMergedIntoCustomerId())
                .addValue("version", e.getAggregateVersion())
                .addValue("createdAt", Timestamp.from(e.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(e.getUpdatedAt()));
        jdbc.update("INSERT INTO crm_customer (" + COLUMNS + ") VALUES (:customerId, :displayName,"
                + " :state, :owner, :mergedInto, :version, :createdAt, :updatedAt)", params);
        return customer;
    }

    @Override
    public Customer update(Customer customer, long expectedAggregateVersion) {
        SimpleCrmServerCustomerEntity e = SimpleCrmServerCustomerEntity.from(customer);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", e.getCustomerId()).addValue("displayName", e.getDisplayName())
                .addValue("state", e.getStateCode()).addValue("owner", e.getOwner())
                .addValue("mergedInto", e.getMergedIntoCustomerId())
                .addValue("newVersion", e.getAggregateVersion())
                .addValue("expectedVersion", expectedAggregateVersion)
                .addValue("updatedAt", Timestamp.from(e.getUpdatedAt()));
        int updated = jdbc.update("UPDATE crm_customer SET display_name = :displayName, state = :state,"
                + " owner = :owner, merged_into_customer_id = :mergedInto, aggregate_version = :newVersion,"
                + " updated_at = :updatedAt WHERE customer_id = :customerId"
                + " AND aggregate_version = :expectedVersion", params);
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "customer");
        }
        return customer;
    }

    /**
     * keyset 游标列表（id 升序；cursor 为上一页末行 id 的不透明值）。
     *
     * @param ownerScope 归属主体范围（单 owner 或 IN 集合由调用方展开）
     * @param stateCode 状态过滤（可空）
     * @param keyword 名称关键字包含匹配（显示名或客户 ID，可空）
     * @param cursor 游标（可空=首页）
     * @param limit 每页上限
     * @return 当页行
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Customer> listByOwner(
            List<String> ownerScope, String stateCode, String keyword, Long cursor, int limit) {
        return listByOwner(ownerScope, stateCode, keyword, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param ownerScope 查询条件或分页边界
     * @param stateCode 查询条件或分页边界
     * @param keyword 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Customer> listByOwner(
            List<String> ownerScope, String stateCode, String keyword, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, " + COLUMNS + " FROM crm_customer WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        // ownerScope=null 表示 DATA 授权 all 的全量形态（不加 owner 谓词）
        if (ownerScope != null) {
            sql.append(" AND owner IN (:owners)");
            params.addValue("owners", ownerScope);
        }
        if (stateCode != null) {
            sql.append(" AND state = :state");
            params.addValue("state", stateCode);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (display_name LIKE :kw OR customer_id LIKE :kw)");
            params.addValue("kw", "%" + keyword.trim() + "%");
        }
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<Customer> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return MAPPER.mapRow(rs, rowNum).toDomain();
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Customer>(rows,
                primaryKeys, limit, total);
    }

    /**
     * 查行主键（游标锚点）。
     *
     * @param customerId 客户标识
     * @return 主键 id
     */
    public long primaryKeyOf(String customerId) {
        try {
            return jdbc.queryForObject("SELECT id FROM crm_customer WHERE customer_id = :customerId",
                    new MapSqlParameterSource("customerId", customerId), Long.class);
        } catch (EmptyResultDataAccessException e) {
            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "customer");
        }
    }

    /**
     * 更新归属（合并改挂/转移复用，带 CAS）。
     *
     * @param customerId 客户标识
     * @param newOwner 新归属
     * @param expectedVersion 预期聚合版本
     */
    public void updateOwner(String customerId, String newOwner, long expectedVersion) {
        int updated = jdbc.update("UPDATE crm_customer SET owner = :owner, aggregate_version ="
                        + " aggregate_version + 1, updated_at = :now WHERE customer_id = :customerId"
                        + " AND aggregate_version = :expectedVersion",
                new MapSqlParameterSource("owner", newOwner).addValue("customerId", customerId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "customer");
        }
    }
}
