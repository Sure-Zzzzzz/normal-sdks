package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.OpportunityRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerOpportunityEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 商机 JDBC 权威仓储。 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcOpportunityRepository extends SimpleCrmServerJdbcRepositorySupport
        implements OpportunityRepository {
    private static final String COLUMNS = "opportunity_id, customer_id, owner, title, primary_offering_id,"
            + " contact_id, expected_close_date, estimated_amount, currency, stage, lost_reason,"
            + " aggregate_version, created_at, updated_at";
    private static final RowMapper<SimpleCrmServerOpportunityEntity> MAPPER = (rs, rowNum) ->
            new SimpleCrmServerOpportunityEntity(rs.getString("opportunity_id"), rs.getString("customer_id"),
                    rs.getString("owner"), rs.getString("title"), nullableString(rs, "primary_offering_id"),
                    nullableString(rs, "contact_id"), rs.getDate("expected_close_date") == null ? null
                            : rs.getDate("expected_close_date").toLocalDate(),
                    rs.getBigDecimal("estimated_amount"), nullableString(rs, "currency"),
                    rs.getString("stage"), nullableString(rs, "lost_reason"), rs.getLong("aggregate_version"),
                    instant(rs, "created_at"), instant(rs, "updated_at"));

    public SimpleCrmServerJdbcOpportunityRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public Optional<Opportunity> findById(String opportunityId) {
        return queryOne("SELECT " + COLUMNS + " FROM crm_opportunity WHERE opportunity_id = :opportunityId",
                new MapSqlParameterSource("opportunityId", opportunityId), MAPPER)
                .map(SimpleCrmServerOpportunityEntity::toDomain);
    }

    @Override
    public Opportunity insert(Opportunity value) {
        SimpleCrmServerOpportunityEntity e = SimpleCrmServerOpportunityEntity.from(value);
        jdbc.update("INSERT INTO crm_opportunity (" + COLUMNS + ") VALUES (:opportunityId, :customerId, :owner,"
                        + " :title, :primaryOfferingId, :contactId, :expectedCloseDate, :estimatedAmount, :currency,"
                        + " :stage, :lostReason, :aggregateVersion, :createdAt, :updatedAt)", params(e));
        return value;
    }

    @Override
    public Opportunity update(Opportunity value, long expectedAggregateVersion) {
        SimpleCrmServerOpportunityEntity e = SimpleCrmServerOpportunityEntity.from(value);
        MapSqlParameterSource p = params(e).addValue("expectedVersion", expectedAggregateVersion);
        int count = jdbc.update("UPDATE crm_opportunity SET owner = :owner, stage = :stage, lost_reason = :lostReason,"
                + " aggregate_version = :aggregateVersion, updated_at = :updatedAt"
                + " WHERE opportunity_id = :opportunityId AND aggregate_version = :expectedVersion", p);
        if (count != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "opportunity");
        }
        return value;
    }

    /** 客户合并时批量改挂未终态商机，并统一到目标客户归属。 */
    public int relocateCustomer(String sourceCustomerId, String targetCustomerId, String newOwner) {
        return jdbc.update("UPDATE crm_opportunity SET customer_id = :targetCustomerId, owner = :owner,"
                        + " aggregate_version = aggregate_version + 1, updated_at = :updatedAt"
                        + " WHERE customer_id = :sourceCustomerId AND stage NOT IN ('won', 'lost')",
                new MapSqlParameterSource("targetCustomerId", targetCustomerId)
                        .addValue("owner", newOwner)
                        .addValue("sourceCustomerId", sourceCustomerId)
                        .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    /** 按客户查未终态商机（客户转移归属编排用）。 */
    public List<Opportunity> findOpenByCustomer(String customerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_opportunity WHERE customer_id = :customerId"
                        + " AND stage NOT IN ('won', 'lost')",
                new MapSqlParameterSource("customerId", customerId),
                (rs, rowNum) -> MAPPER.mapRow(rs, rowNum).toDomain());
    }

    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Opportunity> listByOwner(
            List<String> owners, String stage, String customerId, String keyword,
            Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder("SELECT id, " + COLUMNS + " FROM crm_opportunity WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (owners != null) { sql.append(" AND owner IN (:owners)"); p.addValue("owners", owners); }
        if (stage != null && !stage.trim().isEmpty()) { sql.append(" AND stage = :stage"); p.addValue("stage", stage); }
        if (customerId != null && !customerId.trim().isEmpty()) { sql.append(" AND customer_id = :customerId"); p.addValue("customerId", customerId); }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (opportunity_id LIKE :keyword OR title LIKE :keyword)");
            p.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (cursor != null) { sql.append(" AND id > :cursor"); p.addValue("cursor", cursor); }
        Long total = prepareWindow(sql, p, limit, request);
        List<Long> ids = new ArrayList<Long>();
        List<Opportunity> rows = jdbc.query(sql.toString(), p, (rs, rowNum) -> {
            ids.add(rs.getLong("id"));
            return MAPPER.mapRow(rs, rowNum).toDomain();
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Opportunity>(rows, ids, limit, total);
    }

    public Long countByOwner(List<String> owners, String stage, String customerId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM crm_opportunity WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (owners != null) { sql.append(" AND owner IN (:owners)"); p.addValue("owners", owners); }
        if (stage != null && !stage.trim().isEmpty()) { sql.append(" AND stage = :stage"); p.addValue("stage", stage); }
        if (customerId != null && !customerId.trim().isEmpty()) { sql.append(" AND customer_id = :customerId"); p.addValue("customerId", customerId); }
        if (keyword != null && !keyword.trim().isEmpty()) { sql.append(" AND (opportunity_id LIKE :keyword OR title LIKE :keyword)"); p.addValue("keyword", "%" + keyword.trim() + "%"); }
        return jdbc.queryForObject(sql.toString(), p, Long.class);
    }

    private MapSqlParameterSource params(SimpleCrmServerOpportunityEntity e) {
        return new MapSqlParameterSource().addValue("opportunityId", e.getOpportunityId())
                .addValue("customerId", e.getCustomerId()).addValue("owner", e.getOwner())
                .addValue("title", e.getTitle()).addValue("primaryOfferingId", e.getPrimaryOfferingId())
                .addValue("contactId", e.getContactId()).addValue("expectedCloseDate",
                        e.getExpectedCloseDate() == null ? null : Date.valueOf(e.getExpectedCloseDate()))
                .addValue("estimatedAmount", e.getEstimatedAmount()).addValue("currency", e.getCurrency())
                .addValue("stage", e.getStage()).addValue("lostReason", e.getLostReason())
                .addValue("aggregateVersion", e.getAggregateVersion())
                .addValue("createdAt", Timestamp.from(e.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(e.getUpdatedAt()));
    }
}
