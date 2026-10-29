package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerFulfillmentConsumerEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

/**
 * 履约消费者注册表 JDBC 仓储（无端点无凭据；consumerId 须绑定 AKP clientId）。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcFulfillmentConsumerRepository extends SimpleCrmServerJdbcRepositorySupport {

    private static final String COLUMNS =
            "consumer_id, capability, protocol_version, enabled, aggregate_version, created_at,"
                    + " updated_at";

    private static final RowMapper<SimpleCrmServerFulfillmentConsumerEntity> MAPPER = (rs, rowNum) ->
            new SimpleCrmServerFulfillmentConsumerEntity(rs.getString("consumer_id"), rs.getString("capability"),
                    rs.getInt("protocol_version"), rs.getBoolean("enabled"),
                    rs.getLong("aggregate_version"), instant(rs, "created_at"),
                    instant(rs, "updated_at"));

    /**
     * 创建消费者注册表仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcFulfillmentConsumerRepository(
            org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    /**
     * 按能力查启用中的消费者，按注册表主键升序供选择器稳定选取。
     *
     * @param capability 所需消费者能力
     * @return 启用中的消费者集合
     */
    public List<FulfillmentConsumer> findEnabled(String capability) {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_fulfillment_consumer WHERE capability ="
                        + " :capability AND enabled = TRUE ORDER BY id ASC",
                new MapSqlParameterSource("capability", capability),
                (rs, rowNum) -> {
                    SimpleCrmServerFulfillmentConsumerEntity e = MAPPER.mapRow(rs, rowNum);
                    return new FulfillmentConsumer(e.getConsumerId(), e.getCapability(),
                            e.getProtocolVersion(), e.isEnabled());
                });
    }

    /**
     * 注册或更新消费者（按 consumer+capability 唯一键 upsert）。
     *
     * @param consumer 消费者事实
     */
    public void upsert(FulfillmentConsumer consumer) {
        jdbc.update("INSERT INTO crm_fulfillment_consumer (" + COLUMNS + ") VALUES (:consumerId,"
                        + " :capability, :protocolVersion, :enabled, 1, :now, :now) ON DUPLICATE KEY"
                        + " UPDATE protocol_version = :protocolVersion, enabled = :enabled,"
                        + " aggregate_version = aggregate_version + 1, updated_at = :now",
                new MapSqlParameterSource("consumerId", consumer.getConsumerId())
                        .addValue("capability", consumer.getCapability())
                        .addValue("protocolVersion", consumer.getProtocolVersion())
                        .addValue("enabled", consumer.isEnabled())
                        .addValue("now", java.sql.Timestamp.from(java.time.Instant.now())));
    }

    /**
     * 全量列表（管理面）。
     *
     * @return 全部消费者
     */
    public List<FulfillmentConsumer> listAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM crm_fulfillment_consumer ORDER BY id ASC",
                new MapSqlParameterSource(), (rs, rowNum) -> {
                    SimpleCrmServerFulfillmentConsumerEntity e = MAPPER.mapRow(rs, rowNum);
                    return new FulfillmentConsumer(e.getConsumerId(), e.getCapability(),
                            e.getProtocolVersion(), e.isEnabled());
                });
    }

    /**
     * 消费者管理面真实页码列表。
     *
     * @param request 页码请求
     * @return 当页数据和真实总数
     */
    public io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage<FulfillmentConsumer> pageAll(
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM crm_fulfillment_consumer WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long total = prepareWindow(sql, params, request.getPageSize(), request);
        List<FulfillmentConsumer> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            SimpleCrmServerFulfillmentConsumerEntity entity = MAPPER.mapRow(rs, rowNum);
            return new FulfillmentConsumer(entity.getConsumerId(), entity.getCapability(),
                    entity.getProtocolVersion(), entity.isEnabled());
        });
        return new io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage<FulfillmentConsumer>(rows, request, total);
    }
}
