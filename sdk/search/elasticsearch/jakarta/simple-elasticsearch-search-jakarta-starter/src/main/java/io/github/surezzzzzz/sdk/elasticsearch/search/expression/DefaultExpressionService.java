package io.github.surezzzzzz.sdk.elasticsearch.search.expression;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionValidationRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.CursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.model.CursorState;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.Expression;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ParseOptions;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * 只适配 AST；字段能力、实际执行和游标仍经过公开的 Search 契约。
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultExpressionService implements ExpressionService {
    private final ConditionExpressionParser parser;
    private final SimpleElasticsearchSearchProperties properties;
    private final SearchIndexResolver indices;
    private final MappingManager mappings;
    private final SearchDslBuilder queries;
    private final SearchEngine engine;
    private final SearchPayloadCodec codec;
    private final CursorTokenCodec cursors;

    /**
     * 默认使用索引时区与单次固定的当前时刻。
     */
    @Override
    public QueryCondition translate(String expression, String index) {
        return translate(expression, index, null, null, null);
    }

    /**
     * 显式上下文不保存到共享实例；同一返回树可用于多个结构化查询。
     */
    @Override
    public QueryCondition translate(String expression, String index, TimeRangeEnd end, String zone, Instant anchor) {
        return translate(expression, indices.resolve(index, null), end, zone, anchor, false);
    }

    /**
     * 表达式转换后完整进入实际装配的查询 Engine。
     */
    @Override
    public QueryResponse query(ExpressionQueryRequest original) {
        ExpressionQueryRequest request = codec.copy(original, ExpressionQueryRequest.class);
        PaginationType type = paginationType(request);
        boolean stable = type != PaginationType.OFFSET;
        QueryCondition condition = translate(request.getExpression(), queryTarget(request, type),
                request.getTimeRangeEnd(), request.getTimeZone(), request.getTimeRangeAnchor(), stable);
        return engine.query(QueryRequest.builder().index(request.getIndex()).dateRange(request.getDateRange()).query(condition)
                .pagination(request.getPagination()).fields(request.getFields()).collapse(request.getCollapse()).countOnly(request.getCountOnly()).build());
    }

    /**
     * 聚合只转换过滤条件，不绕过聚合定义及 after 校验。
     */
    @Override
    public AggResponse aggregate(ExpressionAggRequest original) {
        ExpressionAggRequest request = codec.copy(original, ExpressionAggRequest.class);
        boolean stable = composite(request.getAggs());
        QueryCondition condition = translate(request.getExpression(), indices.resolve(request.getIndex(), request.getDateRange()),
                request.getTimeRangeEnd(), request.getTimeZone(), request.getTimeRangeAnchor(), stable);
        return engine.aggregate(AggRequest.builder().index(request.getIndex()).dateRange(request.getDateRange()).query(condition)
                .aggs(request.getAggs()).after(request.getAfter()).build());
    }

    /**
     * 不执行搜索；只有参数或字段拒绝可返回 valid=false。
     */
    @Override
    public ExpressionValidationResponse validate(ExpressionValidationRequest original) {
        try {
            ExpressionValidationRequest request = codec.copy(original, ExpressionValidationRequest.class);
            translate(request.getExpression(), indices.resolve(request.getIndex(), request.getDateRange()), request.getTimeRangeEnd(),
                    request.getTimeZone(), request.getTimeRangeAnchor(), false);
            return new ExpressionValidationResponse(true, null);
        } catch (SimpleElasticsearchSearchException error) {
            if (error.getStatus() != HTTP_BAD_REQUEST) throw error;
            // 可替换扩展的异常也不透传原始消息，HTTP 与 Java 校验均保持安全提示。
            String message = ErrorCode.FIELD_INVALID.equals(error.getErrorCode())
                    ? ErrorMessage.FIELD_INVALID : ErrorMessage.REQUEST_INVALID;
            return new ExpressionValidationResponse(false, message);
        }
    }

    /**
     * 提示返回字段元数据，不枚举值，不把冲突、nested 或禁止字段伪装为可查询字段。
     */
    @Override
    public ExpressionHintsResponse hints(String index) {
        ResolvedIndex target = indices.resolve(index, null);
        Map<String, FieldMetadata> fields = mappings.fields(target);
        List<FieldMetadata> visible = new ArrayList<>();
        for (FieldMetadata field : fields.values()) {
            try {
                queries.field(field.getName(), target, fields, false);
                List<String> labels = target.getConfig().getFieldMapping().getOrDefault(field.getName(), Collections.emptyList());
                visible.add(field.withLabels(Collections.unmodifiableList(new ArrayList<>(labels))));
            } catch (SimpleElasticsearchSearchException error) {
                if (error.getStatus() != HTTP_BAD_REQUEST) throw error;
            }
        }
        return new ExpressionHintsResponse(Collections.unmodifiableList(visible), ExpressionSyntaxConstant.OPERATORS,
                Collections.unmodifiableList(new ArrayList<>(TimeRange.getAllKeywords().keySet())));
    }

    private QueryCondition translate(String expression, ResolvedIndex target, TimeRangeEnd end, String zone, Instant anchor, boolean stable) {
        int length = expression == null ? 0 : expression.length();
        log.debug("表达式转换开始 length={} stableTime={}", length, stable);
        try {
            ParseOptions options = options();
            Expression ast;
            try {
                ast = parser.parse(expression, options);
            } catch (ConditionExpressionParseException error) {
                // parser 异常可能持有原文，不能保留原因链或透传原始消息。
                log.debug("表达式语法拒绝 category={} line={} column={}", error.getErrorType(), error.getLine(), error.getColumn());
                throw invalid();
            }
            ZoneId actualZone = ZoneId.of(zone == null ? target.getConfig().getZoneId() : zone);
            Map<String, FieldMetadata> fields = mappings.fields(target);
            Map<String, String> labels = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : target.getConfig().getFieldMapping().entrySet())
                for (String label : entry.getValue()) labels.put(label, entry.getKey());
            QueryCondition result = ast.accept(new ExpressionToQueryConditionVisitor(fields, labels,
                    (anchor == null ? Instant.now() : anchor).atZone(actualZone), end == null ? TimeRangeEnd.NOW : end,
                    stable, anchor != null));
            queries.query(result, target, fields);
            log.debug("表达式转换完成 datasource={} length={}", target.getDatasource(), length);
            return result;
        } catch (SimpleElasticsearchSearchException error) {
            log.debug("表达式转换拒绝 status={}", error.getStatus());
            throw error;
        } catch (DateTimeException | IllegalArgumentException error) {
            log.debug("表达式上下文拒绝 category={}", error.getClass().getSimpleName());
            throw invalid();
        }
    }

    private ParseOptions options() {
        SimpleElasticsearchSearchProperties.QueryLimits limits = properties.getQueryLimits();
        return ParseOptions.builder().maxLength(properties.getExpression().getMaxLength())
                .maxTokens(properties.getExpression().getMaxTokens())
                .maxParseDepth(Math.min(limits.getMaxDepth(), ConditionExpressionParserConstant.MAX_PARSE_DEPTH))
                .maxAstDepth(limits.getMaxDepth()).maxAstNodes(limits.getMaxNodes())
                .maxConditions(limits.getMaxNodes()).maxInValues(limits.getMaxNodes()).build();
    }

    private boolean composite(List<AggDefinition> definitions) {
        if (definitions == null) return false;
        if (definitions.size() > properties.getQueryLimits().getMaxNodes()) throw invalid();
        Deque<AggDefinition> pending = new ArrayDeque<>();
        for (AggDefinition definition : definitions) {
            if (definition == null) throw invalid();
            pending.add(definition);
        }
        int nodes = 0;
        boolean found = false;
        while (!pending.isEmpty()) {
            if (++nodes > properties.getQueryLimits().getMaxNodes()) throw invalid();
            AggDefinition definition = pending.removeFirst();
            found |= Boolean.TRUE.equals(definition.getComposite());
            if (definition.getAggs() != null) for (AggDefinition child : definition.getAggs()) {
                if (child == null || nodes + pending.size() >= properties.getQueryLimits().getMaxNodes())
                    throw invalid();
                pending.add(child);
            }
        }
        return found;
    }

    private PaginationType paginationType(ExpressionQueryRequest request) {
        if (Boolean.TRUE.equals(request.getCountOnly()) || request.getPagination() == null)
            return PaginationType.OFFSET;
        try {
            return request.getPagination().getTypeEnum();
        } catch (IllegalArgumentException error) {
            throw invalid();
        }
    }

    // 续页元数据必须读取 token 的冻结索引；不能在午夜或跨月时重新展开当前日期。
    private ResolvedIndex queryTarget(ExpressionQueryRequest request, PaginationType type) {
        PaginationInfo page = request.getPagination();
        String kind = null;
        if (type == PaginationType.SCROLL) kind = VALUE_SCROLL;
        else if (type == PaginationType.SEARCH_AFTER) {
            try {
                if (page.getSearchAfterModeEnum() == SearchAfterMode.PIT) kind = VALUE_PIT;
            } catch (IllegalArgumentException error) {
                throw invalid();
            }
        }
        String token = kind == null ? null : VALUE_PIT.equals(kind) ? page.getPitId() : page.getScrollId();
        if (!text(token)) return indices.resolve(request.getIndex(), request.getDateRange());
        CursorState state = cursors.decode(token, kind, false);
        ResolvedIndex target = indices.resume(request.getIndex(), state.getIndices(), state.getDatasource(), state.getFrom(), state.getTo(), state.getDowngradeLevel());
        if (!target.getIdentifier().equals(state.getIdentifier())) throw cursor();
        return target;
    }
}
