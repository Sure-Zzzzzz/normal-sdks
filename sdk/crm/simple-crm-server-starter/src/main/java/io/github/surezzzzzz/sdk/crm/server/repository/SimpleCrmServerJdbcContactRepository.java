package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.ContactRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerContactEntity;

/**
 * 联系人 JDBC 仓储（keyset 游标按 id 升序，customerId 过滤）。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcContactRepository extends SimpleCrmServerJdbcRepositorySupport implements ContactRepository {

    private static final String COLUMNS =
            "contact_id, customer_id, display_name, title, state, aggregate_version, created_at, updated_at";

    private static final RowMapper<SimpleCrmServerContactEntity> MAPPER = (rs, rowNum) -> new SimpleCrmServerContactEntity(
            rs.getString("contact_id"), rs.getString("customer_id"), rs.getString("display_name"),
            nullableString(rs, "title"), rs.getString("state"), rs.getLong("aggregate_version"),
            instant(rs, "created_at"), instant(rs, "updated_at"));

    /**
     * 创建联系人仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcContactRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public Optional<Contact> findById(String contactId) {
        return queryOne("SELECT " + COLUMNS + " FROM crm_contact WHERE contact_id = :contactId",
                new MapSqlParameterSource("contactId", contactId), MAPPER).map(SimpleCrmServerContactEntity::toDomain);
    }

    @Override
    public Contact insert(Contact contact) {
        SimpleCrmServerContactEntity e = SimpleCrmServerContactEntity.from(contact);
        jdbc.update("INSERT INTO crm_contact (" + COLUMNS + ") VALUES (:contactId, :customerId,"
                        + " :displayName, :title, :state, :version, :createdAt, :updatedAt)",
                new MapSqlParameterSource().addValue("contactId", e.getContactId())
                        .addValue("customerId", e.getCustomerId()).addValue("displayName", e.getDisplayName())
                        .addValue("title", e.getTitle()).addValue("state", e.getStateCode())
                        .addValue("version", e.getAggregateVersion())
                        .addValue("createdAt", Timestamp.from(e.getCreatedAt()))
                        .addValue("updatedAt", Timestamp.from(e.getUpdatedAt())));
        return contact;
    }

    @Override
    public Contact update(Contact contact, long expectedAggregateVersion) {
        SimpleCrmServerContactEntity e = SimpleCrmServerContactEntity.from(contact);
        int updated = jdbc.update("UPDATE crm_contact SET customer_id = :customerId, display_name ="
                        + " :displayName, title = :title, state = :state, aggregate_version = :newVersion,"
                        + " updated_at = :updatedAt WHERE contact_id = :contactId"
                        + " AND aggregate_version = :expectedVersion",
                new MapSqlParameterSource().addValue("contactId", e.getContactId())
                        .addValue("customerId", e.getCustomerId()).addValue("displayName", e.getDisplayName())
                        .addValue("title", e.getTitle()).addValue("state", e.getStateCode())
                        .addValue("newVersion", e.getAggregateVersion())
                        .addValue("expectedVersion", expectedAggregateVersion)
                        .addValue("updatedAt", Timestamp.from(e.getUpdatedAt())));
        if (updated != 1) {
            throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                    io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.AGGREGATE_VERSION_CONFLICT,
                    "contact");
        }
        return contact;
    }

    /**
     * 按客户 keyset 列表。
     *
     * @param customerId 客户标识
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页行
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Contact> listByCustomer(
            String customerId, Long cursor, int limit) {
        return listByCustomer(customerId, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param customerId 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Contact> listByCustomer(
            String customerId, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, " + COLUMNS + " FROM crm_contact WHERE customer_id = :customerId");
        MapSqlParameterSource params = new MapSqlParameterSource("customerId", customerId)
                .addValue("limit", limit);
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<Contact> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return MAPPER.mapRow(rs, rowNum).toDomain();
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Contact>(rows,
                primaryKeys, limit, total);
    }

    /**
     * 合并改挂：同事务把源客户联系人指向目标客户。
     *
     * @param sourceCustomerId 源客户标识
     * @param targetCustomerId 目标客户标识
     * @return 改挂行数
     */
    public int relocateCustomer(String sourceCustomerId, String targetCustomerId) {
        return jdbc.update("UPDATE crm_contact SET customer_id = :target, aggregate_version ="
                        + " aggregate_version + 1, updated_at = :now WHERE customer_id = :source",
                new MapSqlParameterSource("target", targetCustomerId)
                        .addValue("source", sourceCustomerId)
                        .addValue("now", Timestamp.from(Instant.now())));
    }
}
