package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationLine;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.repository.QuotationRepository;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerQuotationEntity;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerQuotationVersionEntity;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerJsonCodec;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 报价 JDBC 仓储：聚合/版本/行三表一体装配；版本行保存 JSON 快照。
 *
 * <p>聚合查询返回带当前版本+行集合的完整事实（findVersion 单独提供按版本号读取）；
 * 版本与行项的插入必须在调用方同一事务内完成。</p>
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcQuotationRepository extends SimpleCrmServerJdbcRepositorySupport implements QuotationRepository {

    private static final String QUOTATION_COLUMNS =
            "quotation_id, customer_id, opportunity_id, owner, aggregate_version, current_version,"
                    + " current_confirmable_version, confirmed_order_id, supersedes_quotation_id,"
                    + " created_at, updated_at";

    private static final RowMapper<SimpleCrmServerQuotationEntity> QUOTATION_MAPPER = (rs, rowNum) -> new SimpleCrmServerQuotationEntity(
            rs.getString("quotation_id"), rs.getString("customer_id"), rs.getString("opportunity_id"), rs.getString("owner"),
            rs.getLong("aggregate_version"), rs.getInt("current_version"),
            nullableInt(rs, "current_confirmable_version"), nullableString(rs, "confirmed_order_id"),
            nullableString(rs, "supersedes_quotation_id"), instant(rs, "created_at"),
            instant(rs, "updated_at"));

    /**
     * 创建报价仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcQuotationRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public Optional<Quotation> findById(String quotationId) {
        return queryOne("SELECT " + QUOTATION_COLUMNS + " FROM crm_quotation WHERE quotation_id ="
                + " :quotationId", new MapSqlParameterSource("quotationId", quotationId),
                QUOTATION_MAPPER).map(SimpleCrmServerQuotationEntity::toDomain);
    }

    @Override
    public Optional<QuotationVersion> findVersion(String quotationId, int version) {
        Optional<SimpleCrmServerQuotationVersionEntity> entity = queryOne(
                "SELECT quotation_id, version, state, settlement_currency, valid_until, total_amount,"
                        + " issued_at, issued_by, terminal_at, terminal_by, aggregate_version, created_at,"
                        + " updated_at FROM crm_quotation_version WHERE quotation_id = :quotationId"
                        + " AND version = :version",
                new MapSqlParameterSource("quotationId", quotationId).addValue("version", version),
                VERSION_MAPPER);
        if (!entity.isPresent()) {
            return Optional.empty();
        }
        List<QuotationLine> lines = loadLines(quotationId, version);
        return Optional.of(assemble(entity.get(), lines));
    }

    private static final RowMapper<SimpleCrmServerQuotationVersionEntity> VERSION_MAPPER = (rs, rowNum) ->
            new SimpleCrmServerQuotationVersionEntity(rs.getString("quotation_id"), rs.getInt("version"),
                    rs.getString("state"), rs.getString("settlement_currency"),
                    instant(rs, "valid_until"), rs.getBigDecimal("total_amount"),
                    instant(rs, "issued_at"), nullableString(rs, "issued_by"),
                    instant(rs, "terminal_at"), nullableString(rs, "terminal_by"),
                    rs.getLong("aggregate_version"), instant(rs, "created_at"),
                    instant(rs, "updated_at"));

    @Override
    public Quotation insert(Quotation quotation) {
        SimpleCrmServerQuotationEntity e = SimpleCrmServerQuotationEntity.from(quotation);
        jdbc.update("INSERT INTO crm_quotation (" + QUOTATION_COLUMNS + ") VALUES (:quotationId,"
                        + " :customerId, :opportunityId, :owner, :version, :currentVersion, :confirmableVersion,"
                        + " :confirmedOrderId, :supersedesId, :createdAt, :updatedAt)",
                quotationParams(e));
        return quotation;
    }

    @Override
    public Quotation update(Quotation quotation, long expectedAggregateVersion) {
        SimpleCrmServerQuotationEntity e = SimpleCrmServerQuotationEntity.from(quotation);
        MapSqlParameterSource params = quotationParams(e)
                .addValue("expectedVersion", expectedAggregateVersion);
        int updated = jdbc.update("UPDATE crm_quotation SET customer_id = :customerId, opportunity_id = :opportunityId, owner = :owner,"
                + " aggregate_version = :version, current_version = :currentVersion,"
                + " current_confirmable_version = :confirmableVersion, confirmed_order_id ="
                + " :confirmedOrderId, supersedes_quotation_id = :supersedesId, updated_at = :updatedAt"
                + " WHERE quotation_id = :quotationId AND aggregate_version = :expectedVersion", params);
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "quotation");
        }
        return quotation;
    }

    @Override
    public QuotationVersion insertVersion(QuotationVersion quotationVersion) {
        insertVersionRow(quotationVersion);
        insertLines(quotationVersion);
        return quotationVersion;
    }

    /**
     * 编辑草稿的版本行整版替换（版本号不变，先删旧行再插入新行+新行明细）。
     *
     * @param quotationVersion 编辑后版本事实（版本号与已有行一致）
     * @return 版本事实
     */
    public QuotationVersion replaceVersion(QuotationVersion quotationVersion) {
        jdbc.update("DELETE FROM crm_quotation_line WHERE quotation_id = :quotationId"
                        + " AND version = :version",
                new MapSqlParameterSource("quotationId", quotationVersion.getQuotationId())
                        .addValue("version", quotationVersion.getVersion()));
        jdbc.update("DELETE FROM crm_quotation_version WHERE quotation_id = :quotationId"
                        + " AND version = :version",
                new MapSqlParameterSource("quotationId", quotationVersion.getQuotationId())
                        .addValue("version", quotationVersion.getVersion()));
        insertVersionRow(quotationVersion);
        insertLines(quotationVersion);
        return quotationVersion;
    }

    @Override
    public QuotationVersion transitionVersionState(QuotationVersion quotationVersion,
                                                   QuotationState expectedState) {
        // 迁移须同时落 issued/terminal 时间与操作人（读回装配按状态槽位校验非空）
        int updated = jdbc.update("UPDATE crm_quotation_version SET state = :newState,"
                        + " issued_at = :issuedAt, issued_by = :issuedBy, terminal_at = :terminalAt,"
                        + " terminal_by = :terminalBy, aggregate_version = aggregate_version + 1,"
                        + " updated_at = :updatedAt WHERE quotation_id = :quotationId"
                        + " AND version = :version AND state = :expectedState",
                new MapSqlParameterSource("newState", quotationVersion.getState().getCode())
                        .addValue("issuedAt", quotationVersion.getIssuedAt() == null ? null
                                : Timestamp.from(quotationVersion.getIssuedAt()))
                        .addValue("issuedBy", quotationVersion.getIssuedBy())
                        .addValue("terminalAt", quotationVersion.getTerminalAt() == null ? null
                                : Timestamp.from(quotationVersion.getTerminalAt()))
                        .addValue("terminalBy", quotationVersion.getTerminalBy())
                        .addValue("quotationId", quotationVersion.getQuotationId())
                        .addValue("version", quotationVersion.getVersion())
                        .addValue("expectedState", expectedState.getCode())
                        .addValue("updatedAt", Timestamp.from(Instant.now())));
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "quotationVersion");
        }
        return quotationVersion;
    }

    /**
     * keyset 列表（owner 范围+状态过滤；expired 为派生态由服务层按 valid_until 表达）。
     *
     * @param ownerScope owner 范围集合
     * @param stateCode 状态过滤（可空）
     * @param keyword 单号或客户名关键字包含匹配（可空）
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页聚合（不含行）
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Quotation> listByOwner(
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
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Quotation> listByOwner(
            List<String> ownerScope, String stateCode, String keyword, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder(
                "SELECT q.id, q.quotation_id, q.customer_id, q.opportunity_id, q.owner,"
                        + " q.aggregate_version, q.current_version, q.current_confirmable_version,"
                        + " q.confirmed_order_id, q.supersedes_quotation_id, q.created_at, q.updated_at"
                        + " FROM crm_quotation q JOIN crm_quotation_version qv"
                        + " ON qv.quotation_id = q.quotation_id AND qv.version = q.current_version"
                        + " WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        // ownerScope=null 表示 DATA 授权 all 的全量形态（不加 owner 谓词）
        if (ownerScope != null) {
            sql.append(" AND q.owner IN (:owners)");
            params.addValue("owners", ownerScope);
        }
        if (stateCode != null && !stateCode.trim().isEmpty()) {
            String normalizedState = stateCode.trim().toLowerCase(Locale.ROOT);
            if ("draft".equals(normalizedState)) {
                sql.append(" AND qv.state = 'draft'");
            } else if ("issued".equals(normalizedState)) {
                sql.append(" AND qv.state = 'issued' AND qv.valid_until > :stateNow");
                params.addValue("stateNow", Timestamp.from(Instant.now()));
            } else if ("confirmed".equals(normalizedState)) {
                sql.append(" AND qv.state = 'confirmed'");
            } else if ("withdrawn".equals(normalizedState)) {
                sql.append(" AND qv.state = 'withdrawn'");
            } else if ("superseded".equals(normalizedState)) {
                sql.append(" AND qv.state = 'superseded'");
            } else if ("expired".equals(normalizedState)) {
                sql.append(" AND qv.state = 'issued' AND qv.valid_until <= :stateNow");
                params.addValue("stateNow", Timestamp.from(Instant.now()));
            } else {
                throw CrmException.validation("state");
            }
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (q.quotation_id LIKE :kw OR EXISTS (SELECT 1 FROM crm_customer c"
                    + " WHERE c.customer_id = q.customer_id AND c.display_name LIKE :kw))");
            params.addValue("kw", "%" + keyword.trim() + "%");
        }
        if (cursor != null) {
            sql.append(" AND q.id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<Quotation> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return QUOTATION_MAPPER.mapRow(rs, rowNum).toDomain();
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<Quotation>(rows,
                primaryKeys, limit, total);
    }

    /**
     * 按客户查未完结报价（合并/转移编排用）。
     *
     * @param customerId 客户标识
     * @return 聚合列表
     */
    public List<Quotation> findOpenByCustomer(String customerId) {
        return jdbc.query("SELECT q." + QUOTATION_COLUMNS.replace(", ", ", q.")
                        + " FROM crm_quotation q JOIN crm_quotation_version qv"
                        + " ON qv.quotation_id = q.quotation_id AND qv.version = q.current_version"
                        + " WHERE q.customer_id = :customerId"
                        + " AND (qv.state = 'draft' OR (qv.state = 'issued' AND qv.valid_until > :now))",
                new MapSqlParameterSource("customerId", customerId)
                        .addValue("now", Timestamp.from(Instant.now())),
                (rs, rowNum) -> QUOTATION_MAPPER.mapRow(rs, rowNum).toDomain());
    }

    /**
     * 按商机查未完结报价（商机转移编排用）。
     *
     * @param opportunityId 商机标识
     * @return 聚合列表
     */
    public List<Quotation> findOpenByOpportunity(String opportunityId) {
        return jdbc.query("SELECT q." + QUOTATION_COLUMNS.replace(", ", ", q.")
                        + " FROM crm_quotation q JOIN crm_quotation_version qv"
                        + " ON qv.quotation_id = q.quotation_id AND qv.version = q.current_version"
                        + " WHERE q.opportunity_id = :opportunityId"
                        + " AND (qv.state = 'draft' OR (qv.state = 'issued' AND qv.valid_until > :now))",
                new MapSqlParameterSource("opportunityId", opportunityId)
                        .addValue("now", Timestamp.from(Instant.now())),
                (rs, rowNum) -> QUOTATION_MAPPER.mapRow(rs, rowNum).toDomain());
    }

    /**
     * 合并/转移改挂客户（CAS 由聚合版本守卫，整批原子）。
     *
     * @param fromCustomerId 源客户
     * @param toCustomerId 目标客户
     * @param newOwner       目标客户归属
     * @return 改挂行数
     */
    public int relocateCustomer(String fromCustomerId, String toCustomerId, String newOwner) {
        return jdbc.update("UPDATE crm_quotation q JOIN crm_quotation_version qv"
                        + " ON qv.quotation_id = q.quotation_id AND qv.version = q.current_version"
                        + " SET q.customer_id = :target, q.owner = CASE WHEN qv.state IN"
                        + " ('draft') OR (qv.state = 'issued' AND qv.valid_until > :now)"
                        + " THEN :owner ELSE q.owner END, q.aggregate_version ="
                        + " q.aggregate_version + 1, q.updated_at = :now WHERE q.customer_id = :source",
                new MapSqlParameterSource("target", toCustomerId).addValue("owner", newOwner)
                        .addValue("source", fromCustomerId)
                        .addValue("now", Timestamp.from(Instant.now())));
    }

    /**
     * 归属转移：按调用方已读取的报价事实执行 CAS，避免覆盖并发转移。
     *
     * @param quotation 调用方已读取的未完结报价
     * @param newOwner 新归属
     */
    public void updateOwner(Quotation quotation, String newOwner) {
        int updated = jdbc.update("UPDATE crm_quotation SET owner = :owner,"
                        + " aggregate_version = aggregate_version + 1, updated_at = :now"
                        + " WHERE quotation_id = :quotationId AND aggregate_version = :expectedVersion"
                        + " AND owner = :expectedOwner AND customer_id = :customerId"
                        + " AND EXISTS (SELECT 1 FROM crm_quotation_version qv"
                        + " WHERE qv.quotation_id = crm_quotation.quotation_id"
                        + " AND qv.version = crm_quotation.current_version"
                        + " AND (qv.state = 'draft' OR (qv.state = 'issued' AND qv.valid_until > :now)))",
                new MapSqlParameterSource("owner", newOwner)
                        .addValue("quotationId", quotation.getQuotationId())
                        .addValue("expectedVersion", quotation.getAggregateVersion())
                        .addValue("expectedOwner", quotation.getOwner())
                        .addValue("customerId", quotation.getCustomerId())
                        .addValue("now", Timestamp.from(Instant.now())));
        if (updated != 1) {
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "quotation");
        }
    }

    private MapSqlParameterSource quotationParams(SimpleCrmServerQuotationEntity e) {
        return new MapSqlParameterSource().addValue("quotationId", e.getQuotationId())
                .addValue("customerId", e.getCustomerId()).addValue("opportunityId", e.getOpportunityId()).addValue("owner", e.getOwner())
                .addValue("version", e.getAggregateVersion())
                .addValue("currentVersion", e.getCurrentVersion())
                .addValue("confirmableVersion", e.getCurrentConfirmableVersion())
                .addValue("confirmedOrderId", e.getConfirmedOrderId())
                .addValue("supersedesId", e.getSupersedesQuotationId())
                .addValue("createdAt", Timestamp.from(e.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(e.getUpdatedAt()));
    }

    private void insertVersionRow(QuotationVersion v) {
        jdbc.update("INSERT INTO crm_quotation_version (quotation_id, version, state,"
                        + " settlement_currency, valid_until, total_amount, issued_at, issued_by,"
                        + " terminal_at, terminal_by, aggregate_version, created_at, updated_at)"
                        + " VALUES (:quotationId, :version, :state, :currency, :validUntil, :totalAmount,"
                        + " :issuedAt, :issuedBy, :terminalAt, :terminalBy, :aggVersion, :createdAt,"
                        + " :updatedAt)",
                new MapSqlParameterSource("quotationId", v.getQuotationId())
                        .addValue("version", v.getVersion())
                        .addValue("state", v.getState().getCode())
                        .addValue("currency", v.getSettlementCurrency())
                        .addValue("validUntil", Timestamp.from(v.getValidUntil()))
                        .addValue("totalAmount", v.getTotalAmount().getAmount())
                        .addValue("issuedAt", v.getIssuedAt() == null ? null
                                : Timestamp.from(v.getIssuedAt()))
                        .addValue("issuedBy", v.getIssuedBy())
                        .addValue("terminalAt", v.getTerminalAt() == null ? null
                                : Timestamp.from(v.getTerminalAt()))
                        .addValue("terminalBy", v.getTerminalBy()).addValue("aggVersion", 1L)
                        .addValue("createdAt", Timestamp.from(Instant.now()))
                        .addValue("updatedAt", Timestamp.from(Instant.now())));
    }

    private void insertLines(QuotationVersion v) {
        for (QuotationLine line : v.getLines()) {
            Map<String, Object> terms = new HashMap<String, Object>();
            // 首发仅 FIXED_PRICE 能力：快照具体值经具体类读取（接口只暴露能力类型）
            io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot
                    fixedTerms = (io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                    .FixedPriceCommercialTermsSnapshot) line.getCommercialTermsSnapshot();
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
            jdbc.update("INSERT INTO crm_quotation_line (quotation_line_id, quotation_id, version,"
                            + " offering_id, offering_reference, quantity, unit, unit_price, currency,"
                            + " line_total, commercial_terms_json, obligation_template_json, created_at)"
                            + " VALUES (:lineId, :quotationId, :version, :offeringId, :reference,"
                            + " :quantity, :unit, :unitPrice, :currency, :lineTotal, :termsJson,"
                            + " :obligationJson, :createdAt)",
                    new MapSqlParameterSource("lineId", line.getQuotationLineId())
                            .addValue("quotationId", v.getQuotationId())
                            .addValue("version", v.getVersion())
                            .addValue("offeringId", line.getOfferingId())
                            .addValue("reference", line.getOfferingReference())
                            .addValue("quantity", line.getQuantity()).addValue("unit", line.getUnit())
                            .addValue("unitPrice", line.getUnitPrice().getAmount())
                            .addValue("currency", line.getUnitPrice().getCurrency())
                            .addValue("lineTotal", line.getLineTotal().getAmount())
                            .addValue("termsJson", SimpleCrmServerJsonCodec.write(terms))
                            .addValue("obligationJson", SimpleCrmServerJsonCodec.write(obligation))
                            .addValue("createdAt", Timestamp.from(Instant.now())));
        }
    }

    private List<QuotationLine> loadLines(String quotationId, int version) {
        return jdbc.query("SELECT quotation_line_id, offering_id, offering_reference, quantity, unit,"
                        + " unit_price, currency, line_total, commercial_terms_json,"
                        + " obligation_template_json FROM crm_quotation_line WHERE quotation_id ="
                        + " :quotationId AND version = :version ORDER BY id ASC",
                new MapSqlParameterSource("quotationId", quotationId).addValue("version", version),
                (rs, rowNum) -> {
                    Map<String, Object> terms = SimpleCrmServerJsonCodec.readMap(
                            rs.getString("commercial_terms_json"));
                    Map<String, Object> obligation = SimpleCrmServerJsonCodec.readMap(
                            rs.getString("obligation_template_json"));
                    io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot
                            termsSnapshot =
                            new io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                                    .FixedPriceCommercialTermsSnapshot(
                                    new BigDecimal(String.valueOf(terms.get("quantity"))),
                                    String.valueOf(terms.get("unit")),
                                    new Money(new BigDecimal(String.valueOf(terms.get("unitPrice"))),
                                            String.valueOf(terms.get("currency"))));
                    io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate
                            obligationTemplate =
                            new io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                                    .FulfillmentObligationTemplate(
                                    String.valueOf(obligation.get("subjectReference")),
                                    String.valueOf(obligation.get("fulfillmentScope")),
                                    String.valueOf(obligation.get("requiredConsumerCapability")));
                    return new QuotationLine(rs.getString("quotation_line_id"),
                            rs.getString("offering_id"), rs.getString("offering_reference"),
                            rs.getBigDecimal("quantity"), rs.getString("unit"),
                            new Money(rs.getBigDecimal("unit_price"), rs.getString("currency")),
                            new Money(rs.getBigDecimal("line_total"), rs.getString("currency")),
                            termsSnapshot, obligationTemplate);
                });
    }

    /**
     * 批量取一组聚合的当前版本事实（版本 state 与有效截止时间）——撤回/取代后聚合字段与草稿同形，
     * 终态判定唯一真相在版本行 state（DRAFT/WITHDRAWN/SUPERSEDED）。
     *
     * @param quotations 聚合列表（keyset 页 ≤100 行）
     * @return quotationId → [版本 state, validUntil 毫秒]；无版本行（不应发生）不进映射
     */
    public Map<String, Object[]> findCurrentVersionFacts(
            List<io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation> quotations) {
        if (quotations == null || quotations.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        int index = 0;
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation q : quotations) {
            if (index > 0) {
                where.append(" OR ");
            }
            where.append("(quotation_id = :q").append(index).append(" AND version = :v")
                    .append(index).append(')');
            params.addValue("q" + index, q.getQuotationId());
            params.addValue("v" + index, q.getCurrentVersion());
            index++;
        }
        return jdbc.query(
                "SELECT quotation_id, state, valid_until FROM crm_quotation_version WHERE "
                        + where,
                params,
                rs -> {
                    Map<String, Object[]> result = new HashMap<String, Object[]>();
                    while (rs.next()) {
                        result.put(rs.getString("quotation_id"), new Object[]{rs.getString("state"),
                                rs.getTimestamp("valid_until") == null ? null
                                        : rs.getTimestamp("valid_until").toInstant()});
                    }
                    return result;
                });
    }

    private QuotationVersion assemble(SimpleCrmServerQuotationVersionEntity e, List<QuotationLine> lines) {
        return new QuotationVersion(e.getQuotationId(), e.getVersion(), e.state(),
                e.getSettlementCurrency(), e.getValidUntil(), lines,
                new Money(e.getTotalAmount(), e.getSettlementCurrency()), e.getIssuedAt(),
                e.getIssuedBy(), e.getTerminalAt(), e.getTerminalBy());
    }
}
