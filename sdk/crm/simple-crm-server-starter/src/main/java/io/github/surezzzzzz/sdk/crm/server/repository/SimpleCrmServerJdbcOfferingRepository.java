package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.OfferingRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerOfferingEntity;

/**
 * 可销售目录 JDBC 仓储（三态，读对全体持码者开放由服务层控制）。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcOfferingRepository extends SimpleCrmServerJdbcRepositorySupport implements OfferingRepository {

    private static final String COLUMNS =
            "offering_id, offering_reference, display_name, state, capability_type,"
                    + " required_consumer_capability, owner, aggregate_version, created_at, updated_at";

    private static final RowMapper<SimpleCrmServerOfferingEntity> MAPPER = (rs, rowNum) -> new SimpleCrmServerOfferingEntity(
            rs.getString("offering_id"), rs.getString("offering_reference"),
            rs.getString("display_name"), rs.getString("state"), rs.getString("capability_type"),
            rs.getString("required_consumer_capability"), rs.getString("owner"),
            rs.getLong("aggregate_version"), instant(rs, "created_at"), instant(rs, "updated_at"));

    /**
     * 创建目录仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcOfferingRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public Optional<Offering> findById(String offeringId) {
        return queryOne("SELECT " + COLUMNS + " FROM crm_offering WHERE offering_id = :offeringId",
                new MapSqlParameterSource("offeringId", offeringId), MAPPER).map(SimpleCrmServerOfferingEntity::toDomain);
    }

    @Override
    public Optional<Offering> findByReference(String offeringReference) {
        return queryOne("SELECT " + COLUMNS + " FROM crm_offering WHERE offering_reference ="
                        + " :offeringReference",
                new MapSqlParameterSource("offeringReference", offeringReference), MAPPER)
                .map(SimpleCrmServerOfferingEntity::toDomain);
    }

    @Override
    public Offering insert(Offering offering) {
        SimpleCrmServerOfferingEntity e = SimpleCrmServerOfferingEntity.from(offering);
        jdbc.update("INSERT INTO crm_offering (" + COLUMNS + ") VALUES (:offeringId, :reference,"
                        + " :displayName, :state, :capabilityType, :requiredCapability, :owner,"
                        + " :version, :createdAt, :updatedAt)",
                new MapSqlParameterSource().addValue("offeringId", e.getOfferingId())
                        .addValue("reference", e.getOfferingReference())
                        .addValue("displayName", e.getDisplayName()).addValue("state", e.getStateCode())
                        .addValue("capabilityType", e.getCapabilityTypeCode())
                        .addValue("requiredCapability", e.getRequiredConsumerCapability())
                        .addValue("owner", e.getOwner()).addValue("version", e.getAggregateVersion())
                        .addValue("createdAt", Timestamp.from(e.getCreatedAt()))
                        .addValue("updatedAt", Timestamp.from(e.getUpdatedAt())));
        return offering;
    }

    @Override
    public Offering update(Offering offering, long expectedAggregateVersion) {
        SimpleCrmServerOfferingEntity e = SimpleCrmServerOfferingEntity.from(offering);
        int updated = jdbc.update("UPDATE crm_offering SET offering_reference = :reference,"
                        + " display_name = :displayName, state = :state, capability_type = :capabilityType,"
                        + " required_consumer_capability = :requiredCapability, aggregate_version ="
                        + " :newVersion, updated_at = :updatedAt WHERE offering_id = :offeringId"
                        + " AND aggregate_version = :expectedVersion",
                new MapSqlParameterSource().addValue("offeringId", e.getOfferingId())
                        .addValue("reference", e.getOfferingReference())
                        .addValue("displayName", e.getDisplayName()).addValue("state", e.getStateCode())
                        .addValue("capabilityType", e.getCapabilityTypeCode())
                        .addValue("requiredCapability", e.getRequiredConsumerCapability())
                        .addValue("newVersion", e.getAggregateVersion())
                        .addValue("expectedVersion", expectedAggregateVersion)
                        .addValue("updatedAt", Timestamp.from(e.getUpdatedAt())));
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "offering");
        }
        return offering;
    }

    /**
     * 按状态 keyset 列表（读无 DATA，全部行对持码者可见）。
     *
     * @param stateCode 状态过滤（可空）
     * @param keyword 名称关键字包含匹配（显示名、业务键或目录项 ID，可空）
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页行
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Offering> list(
            String stateCode, String keyword, Long cursor, int limit) {
        return list(stateCode, keyword, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param stateCode 查询条件或分页边界
     * @param keyword 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Offering> list(
            String stateCode, String keyword, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, " + COLUMNS + " FROM crm_offering WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        if (stateCode != null) {
            sql.append(" AND state = :state");
            params.addValue("state", stateCode);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (display_name LIKE :kw OR offering_reference LIKE :kw OR offering_id LIKE :kw)");
            params.addValue("kw", "%" + keyword.trim() + "%");
        }
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<Offering> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return MAPPER.mapRow(rs, rowNum).toDomain();
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Offering>(rows,
                primaryKeys, limit, total);
    }
}
