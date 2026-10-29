package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentResult;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.FulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.ReplayableFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerFulfillmentItemEntity;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerJsonCodec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 履约项 JDBC 仓储：状态+版本条件写实现原子认领（首次回写 in_progress 的 CAS）；
 * 拉取按派发归属（机器）或 owner（人员）双轨过滤。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcFulfillmentItemRepository extends SimpleCrmServerJdbcRepositorySupport
        implements ReplayableFulfillmentItemRepository {

    private static final String COLUMNS =
            "fulfillment_id, order_id, order_line_id, owner, assigned_consumer_id,"
                    + " consumer_protocol_version, state, report_sequence, obligation_template_json,"
                    + " result_data, result_note, reported_by, reported_at, aggregate_version,"
                    + " created_at, updated_at";

    private static final RowMapper<SimpleCrmServerFulfillmentItemEntity> MAPPER = (rs, rowNum) -> new SimpleCrmServerFulfillmentItemEntity(
            rs.getString("fulfillment_id"), rs.getString("order_id"), rs.getString("order_line_id"),
            rs.getString("owner"), rs.getString("assigned_consumer_id"),
            rs.getInt("consumer_protocol_version"), rs.getString("state"),
            rs.getInt("report_sequence"), rs.getString("obligation_template_json"),
            nullableString(rs, "result_data"), nullableString(rs, "result_note"),
            nullableString(rs, "reported_by"), instant(rs, "reported_at"),
            rs.getLong("aggregate_version"), instant(rs, "created_at"), instant(rs, "updated_at"));

    /**
     * 创建履约项仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcFulfillmentItemRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public FulfillmentItem insert(FulfillmentItem fulfillmentItem) {
        SimpleCrmServerFulfillmentItemEntity e = fromDomain(fulfillmentItem);
        jdbc.update("INSERT INTO crm_fulfillment_item (" + COLUMNS + ") VALUES (:id, :orderId,"
                        + " :orderLineId, :owner, :consumerId, :protocolVersion, :state, :sequence,"
                        + " :obligationJson, :resultData, :resultNote, :reportedBy, :reportedAt,"
                        + " :version, :createdAt, :updatedAt)",
                entityParams(e, e.getAggregateVersion()));
        return fulfillmentItem;
    }

    @Override
    public List<FulfillmentItem> findByOrderId(String orderId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_fulfillment_item WHERE order_id ="
                + " :orderId ORDER BY id ASC", new MapSqlParameterSource("orderId", orderId),
                (rs, rowNum) -> toDomain(MAPPER.mapRow(rs, rowNum)));
    }

    /**
     * 点查。
     *
     * @param fulfillmentId 履约项标识
     * @return 领域事实
     */
    public Optional<FulfillmentItem> findById(String fulfillmentId) {
        return queryOne("SELECT " + COLUMNS + " FROM crm_fulfillment_item WHERE fulfillment_id ="
                + " :id", new MapSqlParameterSource("id", fulfillmentId), MAPPER)
                .map(this::toDomain);
    }

    /**
     * 在已获取订单锁后读取交付项当前事实，不使用旧事务快照。
     *
     * @param fulfillmentId 交付项标识
     * @return 当前交付项
     */
    public Optional<FulfillmentItem> findByIdForUpdate(String fulfillmentId) {
        return queryOne("SELECT " + COLUMNS + " FROM crm_fulfillment_item WHERE fulfillment_id ="
                        + " :id FOR UPDATE", new MapSqlParameterSource("id", fulfillmentId), MAPPER)
                .map(this::toDomain);
    }

    /**
     * 在订单锁保护下读取整单当前交付事实，用于状态重算。
     *
     * @param orderId 订单标识
     * @return 按主键排序的当前交付项
     */
    public List<FulfillmentItem> findByOrderIdForUpdate(String orderId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_fulfillment_item WHERE order_id ="
                        + " :orderId ORDER BY id ASC FOR UPDATE",
                new MapSqlParameterSource("orderId", orderId),
                (rs, rowNum) -> toDomain(MAPPER.mapRow(rs, rowNum)));
    }

    /**
     * 条件写回（状态+版本 CAS；认领/回写/重置统一入口）。
     *
     * @param item 推进后的领域事实
     * @param expectedVersion 预期聚合版本
     * @param expectedState 预期状态
     */
    public void transition(FulfillmentItem item, long expectedVersion, FulfillmentState expectedState) {
        SimpleCrmServerFulfillmentItemEntity e = fromDomain(item);
        int updated = jdbc.update("UPDATE crm_fulfillment_item SET state = :state, report_sequence ="
                        + " :sequence, result_data = :resultData, result_note = :resultNote,"
                        + " reported_by = :reportedBy, reported_at = :reportedAt, aggregate_version ="
                        + " :newVersion, updated_at = :updatedAt WHERE fulfillment_id = :id AND"
                        + " aggregate_version = :expectedVersion AND state = :expectedState",
                entityParams(e, expectedVersion).addValue("expectedState", expectedState.getCode()));
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "fulfillmentItem");
        }
    }

    /**
     * 消费者拉取：按派发归属+状态 keyset（机器维度）。
     *
     * @param consumerId 消费者标识（=AKP clientId）
     * @param stateCode 状态过滤（可空）
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页行
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem> listByConsumer(
            String consumerId, String stateCode, Long cursor, int limit) {
        return listByConsumer(consumerId, stateCode, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param consumerId 查询条件或分页边界
     * @param stateCode 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem> listByConsumer(
            String consumerId, String stateCode, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        return listCore("assigned_consumer_id = :consumer", consumerId, stateCode, cursor, limit, request);
    }

    /**
     * 人员拉取：按 owner 范围+状态 keyset（人员维度）。
     *
     * @param ownerScope owner 范围集合
     * @param stateCode 状态过滤（可空）
     * @param orderId 订单过滤（可空）
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页行
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem> listByOwner(
            List<String> ownerScope, String stateCode, String orderId, Long cursor, int limit) {
        return listByOwner(ownerScope, stateCode, orderId, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param ownerScope 查询条件或分页边界
     * @param stateCode 查询条件或分页边界
     * @param orderId 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem> listByOwner(
            List<String> ownerScope, String stateCode, String orderId, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder("SELECT id, " + COLUMNS
                + " FROM crm_fulfillment_item WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        // ownerScope=null 表示 DATA 授权 all 的全量形态（人员侧；机器侧走 consumer 谓词）
        if (ownerScope != null) {
            sql.append(" AND owner IN (:owners)");
            params.addValue("owners", ownerScope);
        }
        if (stateCode != null) {
            sql.append(" AND state = :state");
            params.addValue("state", stateCode);
        }
        if (orderId != null) {
            sql.append(" AND order_id = :orderId");
            params.addValue("orderId", orderId);
        }
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<FulfillmentItem> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return toDomain(MAPPER.mapRow(rs, rowNum));
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem>(rows,
                primaryKeys, limit, total);
    }

    private io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem> listCore(
            String condition, String consumerId, String stateCode, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder("SELECT id, " + COLUMNS
                + " FROM crm_fulfillment_item WHERE " + condition);
        MapSqlParameterSource params = new MapSqlParameterSource("consumer", consumerId)
                .addValue("limit", limit);
        if (stateCode != null) {
            sql.append(" AND state = :state");
            params.addValue("state", stateCode);
        }
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<FulfillmentItem> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return toDomain(MAPPER.mapRow(rs, rowNum));
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<FulfillmentItem>(rows,
                primaryKeys, limit, total);
    }

    private MapSqlParameterSource entityParams(SimpleCrmServerFulfillmentItemEntity e, long expectedVersion) {
        return new MapSqlParameterSource("id", e.getFulfillmentId())
                .addValue("orderId", e.getOrderId()).addValue("orderLineId", e.getOrderLineId())
                .addValue("owner", e.getOwner()).addValue("consumerId", e.getAssignedConsumerId())
                .addValue("protocolVersion", e.getConsumerProtocolVersion())
                .addValue("state", e.getStateCode()).addValue("sequence", e.getReportSequence())
                .addValue("obligationJson", e.getObligationTemplateJson())
                .addValue("resultData", e.getResultData()).addValue("resultNote", e.getResultNote())
                .addValue("reportedBy", e.getReportedBy())
                .addValue("reportedAt", e.getReportedAt() == null ? null
                        : Timestamp.from(e.getReportedAt()))
                .addValue("newVersion", e.getAggregateVersion())
                .addValue("expectedVersion", expectedVersion)
                .addValue("version", e.getAggregateVersion())
                .addValue("createdAt", Timestamp.from(e.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(e.getUpdatedAt()));
    }

    private FulfillmentItem toDomain(SimpleCrmServerFulfillmentItemEntity e) {
        Map<String, Object> obligation = SimpleCrmServerJsonCodec.readMap(e.getObligationTemplateJson());
        FulfillmentResult result = e.getReportedBy() == null ? null
                : new FulfillmentResult(e.getReportSequence(), e.getReportedBy(), e.getReportedAt(),
                e.getResultData(), e.getResultNote());
        return new FulfillmentItem(e.getFulfillmentId(), e.getOrderId(), e.getOrderLineId(),
                (int) e.getAggregateVersion(), FulfillmentState.fromCode(e.getStateCode()),
                new FulfillmentObligationTemplate(
                        String.valueOf(obligation.get("subjectReference")),
                        String.valueOf(obligation.get("fulfillmentScope")),
                        String.valueOf(obligation.get("requiredConsumerCapability"))),
                e.getAssignedConsumerId(), e.getConsumerProtocolVersion(), e.getOwner(),
                e.getReportSequence(), result);
    }

    private SimpleCrmServerFulfillmentItemEntity fromDomain(FulfillmentItem item) {
        Map<String, Object> obligation = new HashMap<String, Object>();
        obligation.put("subjectReference", item.getObligationTemplate().getSubjectReference());
        obligation.put("fulfillmentScope", item.getObligationTemplate().getFulfillmentScope());
        obligation.put("requiredConsumerCapability",
                item.getObligationTemplate().getRequiredConsumerCapability());
        FulfillmentResult r = item.getResult();
        return new SimpleCrmServerFulfillmentItemEntity(item.getFulfillmentId(), item.getOrderId(),
                item.getOrderLineId(), item.getOwner(), item.getConsumerId(),
                item.getConsumerProtocolVersion(), item.getState().getCode(),
                item.getReportSequence(), SimpleCrmServerJsonCodec.write(obligation),
                r == null ? null : r.getResultData(), r == null ? null : r.getNote(),
                r == null ? null : r.getReportedBy(), r == null ? null : r.getReportedAt(),
                item.getVersion(), Instant.now(), Instant.now());
    }
}
