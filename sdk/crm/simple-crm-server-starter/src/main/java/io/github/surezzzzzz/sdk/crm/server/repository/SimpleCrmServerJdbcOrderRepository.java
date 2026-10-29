package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;

import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderLine;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.OrderRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerOrderEntity;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerJsonCodec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单 JDBC 仓储（聚合+行一体；确认事务内与履约项同批落库）。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcOrderRepository extends SimpleCrmServerJdbcRepositorySupport implements OrderRepository {

    private static final String ORDER_COLUMNS =
            "order_id, source_quotation_id, source_quotation_version, customer_id,"
                    + " settlement_currency, total_amount, owner, created_at, aggregate_version,"
                    + " display_state, updated_at";

    private static final RowMapper<SimpleCrmServerOrderEntity> ORDER_MAPPER = (rs, rowNum) -> new SimpleCrmServerOrderEntity(
            rs.getString("order_id"), rs.getString("source_quotation_id"),
            rs.getInt("source_quotation_version"), rs.getString("customer_id"),
            rs.getString("settlement_currency"), rs.getBigDecimal("total_amount"),
            rs.getString("owner"), instant(rs, "created_at"), rs.getLong("aggregate_version"),
            rs.getString("display_state"), instant(rs, "updated_at"));

    /**
     * 创建订单仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcOrderRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        Optional<SimpleCrmServerOrderEntity> entity = queryOne("SELECT " + ORDER_COLUMNS + " FROM crm_order WHERE"
                + " order_id = :orderId", new MapSqlParameterSource("orderId", orderId), ORDER_MAPPER);
        if (!entity.isPresent()) {
            return Optional.empty();
        }
        List<OrderLine> lines = loadLines(orderId);
        return Optional.of(assemble(entity.get(), lines));
    }

    @Override
    public Optional<Order> findByQuotationVersion(String quotationId, int quotationVersion) {
        Optional<SimpleCrmServerOrderEntity> entity = queryOne("SELECT " + ORDER_COLUMNS + " FROM crm_order WHERE"
                        + " source_quotation_id = :quotationId AND source_quotation_version = :version",
                new MapSqlParameterSource("quotationId", quotationId).addValue("version",
                        quotationVersion), ORDER_MAPPER);
        if (!entity.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(assemble(entity.get(), loadLines(entity.get().getOrderId())));
    }

    @Override
    public Order insert(Order order) {
        jdbc.update("INSERT INTO crm_order (" + ORDER_COLUMNS + ") VALUES (:orderId,"
                        + " :sourceQuotationId, :sourceQuotationVersion, :customerId, :currency,"
                        + " :totalAmount, :owner, :createdAt, :version, :displayState, :updatedAt)",
                new MapSqlParameterSource("orderId", order.getOrderId())
                        .addValue("sourceQuotationId", order.getSourceQuotationId())
                        .addValue("sourceQuotationVersion", order.getSourceQuotationVersion())
                        .addValue("customerId", order.getCustomerId())
                        .addValue("currency", order.getSettlementCurrency())
                        .addValue("totalAmount", order.getTotalAmount().getAmount())
                        .addValue("owner", order.getOwner())
                        .addValue("createdAt", Timestamp.from(order.getConfirmedAt()))
                        .addValue("version", order.getOrderVersion())
                        .addValue("displayState", order.getDisplayState().getCode())
                        .addValue("updatedAt", Timestamp.from(order.getConfirmedAt())));
        insertLines(order.getOrderId(), order.getLines());
        return order;
    }

    /**
     * 按交付项事实刷新订单展示态（履约回写/重置同事务调用；状态未变化时不写，避免版本噪声）。
     *
     * @param orderId 订单标识
     * @param newState 派生展示态
     * @param now 权威业务时间
     * @return 实际更新行数（0=状态未变化）
     */
    public int updateDisplayStateIfChanged(String orderId,
            io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState newState,
            Instant now) {
        return jdbc.update("UPDATE crm_order SET display_state = :state, updated_at = :now,"
                        + " aggregate_version = aggregate_version + 1 WHERE order_id = :orderId"
                        + " AND display_state <> :state",
                new MapSqlParameterSource("state", newState.getCode())
                        .addValue("now", Timestamp.from(now))
                        .addValue("orderId", orderId));
    }

    /**
     * 获取订单级事务锁。交付写路径必须先锁订单，再锁交付项，统一锁顺序。
     *
     * @param orderId 订单标识
     */
    public void lockById(String orderId) {
        queryOne("SELECT order_id FROM crm_order WHERE order_id = :orderId FOR UPDATE",
                new MapSqlParameterSource("orderId", orderId), (rs, rowNum) -> rs.getString("order_id"))
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "order"));
    }

    /**
     * keyset 列表（owner 范围 + 订单号/客户 ID 关键字包含匹配）。
     *
     * @param ownerScope owner 范围集合
     * @param keyword 订单号或客户 ID 关键字（可空）
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页聚合（含行）
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Order> listByOwner(
            List<String> ownerScope, String keyword, Long cursor, int limit) {
        return listByOwner(ownerScope, keyword, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param ownerScope 查询条件或分页边界
     * @param keyword 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Order> listByOwner(
            List<String> ownerScope, String keyword, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder("SELECT id, " + ORDER_COLUMNS + " FROM crm_order"
                + " WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        // ownerScope=null 表示 DATA 授权 all 的全量形态（不加 owner 谓词）
        if (ownerScope != null) {
            sql.append(" AND owner IN (:owners)");
            params.addValue("owners", ownerScope);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (order_id LIKE :kw OR customer_id LIKE :kw)");
            params.addValue("kw", "%" + keyword.trim() + "%");
        }
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<SimpleCrmServerOrderEntity> entities = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return ORDER_MAPPER.mapRow(rs, rowNum);
        });
        java.util.List<Order> result = new java.util.ArrayList<Order>(entities.size());
        for (SimpleCrmServerOrderEntity e : entities) {
            result.add(assemble(e, loadLines(e.getOrderId())));
        }
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Order>(result,
                primaryKeys, limit, total);
    }

    /**
     * 合并改挂客户（订单不转移 owner，仅客户指向；历史事实）。
     *
     * @param fromCustomerId 源客户
     * @param toCustomerId 目标客户
     * @return 改挂行数
     */
    public int relocateCustomer(String fromCustomerId, String toCustomerId) {
        return jdbc.update("UPDATE crm_order SET customer_id = :target, aggregate_version ="
                        + " aggregate_version + 1, updated_at = :now WHERE customer_id = :source",
                new MapSqlParameterSource("target", toCustomerId).addValue("source", fromCustomerId)
                        .addValue("now", Timestamp.from(Instant.now())));
    }

    private void insertLines(String orderId, List<OrderLine> lines) {
        for (OrderLine line : lines) {
            io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot
                    fixedTerms = (io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                    .FixedPriceCommercialTermsSnapshot) line.getCommercialTermsSnapshot();
            Map<String, Object> terms = new HashMap<String, Object>();
            terms.put("quantity", fixedTerms.getQuantity());
            terms.put("unit", fixedTerms.getUnit());
            terms.put("unitPrice", fixedTerms.getUnitPrice().getAmount());
            terms.put("currency", fixedTerms.getUnitPrice().getCurrency());
            Map<String, Object> obligation = new HashMap<String, Object>();
            obligation.put("subjectReference", line.getFulfillmentObligationTemplate()
                    .getSubjectReference());
            obligation.put("fulfillmentScope", line.getFulfillmentObligationTemplate()
                    .getFulfillmentScope());
            obligation.put("requiredConsumerCapability", line.getFulfillmentObligationTemplate()
                    .getRequiredConsumerCapability());
            jdbc.update("INSERT INTO crm_order_line (order_line_id, order_id, source_quotation_line_id,"
                            + " offering_reference, quantity, unit, unit_price, currency, line_total,"
                            + " commercial_terms_json, obligation_template_json, created_at) VALUES"
                            + " (:lineId, :orderId, :sourceLineId, :reference, :quantity, :unit,"
                            + " :unitPrice, :currency, :lineTotal, :termsJson, :obligationJson,"
                            + " :createdAt)",
                    new MapSqlParameterSource("lineId", line.getOrderLineId())
                            .addValue("orderId", orderId)
                            .addValue("sourceLineId", line.getSourceQuotationLineId())
                            .addValue("reference", line.getOfferingReference())
                            .addValue("quantity", line.getQuantity())
                            .addValue("unit", line.getUnit())
                            .addValue("unitPrice", line.getUnitPrice().getAmount())
                            .addValue("currency", line.getUnitPrice().getCurrency())
                            .addValue("lineTotal", line.getLineTotal().getAmount())
                            .addValue("termsJson", SimpleCrmServerJsonCodec.write(terms))
                            .addValue("obligationJson", SimpleCrmServerJsonCodec.write(obligation))
                            .addValue("createdAt", Timestamp.from(Instant.now())));
        }
    }

    private List<OrderLine> loadLines(String orderId) {
        return jdbc.query("SELECT order_line_id, source_quotation_line_id, offering_reference,"
                        + " quantity, unit, unit_price, currency, line_total, commercial_terms_json,"
                        + " obligation_template_json FROM crm_order_line WHERE order_id = :orderId"
                        + " ORDER BY id ASC",
                new MapSqlParameterSource("orderId", orderId), (rs, rowNum) -> {
                    Map<String, Object> terms = SimpleCrmServerJsonCodec.readMap(
                            rs.getString("commercial_terms_json"));
                    Map<String, Object> obligation = SimpleCrmServerJsonCodec.readMap(
                            rs.getString("obligation_template_json"));
                    return new OrderLine(rs.getString("order_line_id"),
                            rs.getString("source_quotation_line_id"),
                            rs.getString("offering_reference"), rs.getBigDecimal("quantity"),
                            rs.getString("unit"), new Money(rs.getBigDecimal("unit_price"),
                            rs.getString("currency")),
                            new Money(rs.getBigDecimal("line_total"), rs.getString("currency")),
                            new io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                                    .FixedPriceCommercialTermsSnapshot(
                                    new BigDecimal(String.valueOf(terms.get("quantity"))),
                                    String.valueOf(terms.get("unit")),
                                    new Money(new BigDecimal(String.valueOf(terms.get("unitPrice"))),
                                            String.valueOf(terms.get("currency")))),
                            new io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                                    .FulfillmentObligationTemplate(
                                    String.valueOf(obligation.get("subjectReference")),
                                    String.valueOf(obligation.get("fulfillmentScope")),
                                    String.valueOf(obligation.get("requiredConsumerCapability"))));
                });
    }

    private Order assemble(SimpleCrmServerOrderEntity e, List<OrderLine> lines) {
        return new Order(e.getOrderId(), e.getSourceQuotationId(), e.getSourceQuotationVersion(),
                e.getCustomerId(), e.getSettlementCurrency(),
                new Money(e.getTotalAmount(), e.getSettlementCurrency()), e.getOwner(),
                e.getConfirmedAt(), e.getOrderVersion(), e.displayState(), lines);
    }
}
