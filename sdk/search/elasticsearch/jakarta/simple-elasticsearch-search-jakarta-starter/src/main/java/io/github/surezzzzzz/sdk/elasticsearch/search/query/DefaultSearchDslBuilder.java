package io.github.surezzzzzz.sdk.elasticsearch.search.query;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import lombok.RequiredArgsConstructor;

import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * 结构化条件到 REST JSON；未知操作符、冲突 mapping 与 nested 标量查询全部拒绝。
 */
@RequiredArgsConstructor
public class DefaultSearchDslBuilder implements SearchDslBuilder {
    private final SimpleElasticsearchSearchProperties properties;
    private final SensitiveFieldProcessor sensitive;

    /**
     * 编译校验后的查询对象，并追加必要的日期过滤。
     */
    public Map<String, Object> query(QueryCondition condition, ResolvedIndex target, Map<String, FieldMetadata> fields) {
        Map<String, Object> query = condition == null ? node(VALUE_MATCH_ALL, Collections.emptyMap()) : condition(condition, target, fields, 0, new int[]{0});
        if (target.getFrom() != null && properties.getQueryLimits().isStrictDateFilter()) {
            String field = field(target.getConfig().getDateField(), target, fields, false);
            FieldMetadata metadata = fields.get(field);
            if (metadata == null || !Arrays.asList(VALUE_DATE, VALUE_DATE_NANOS).contains(metadata.getType()))
                throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
            Map<String, Object> bounds = new LinkedHashMap<>();
            bounds.put(VALUE_GTE, target.getFrom());
            bounds.put(VALUE_LT, target.getTo());
            bounds.put(VALUE_FORMAT, VALUE_STRICT_DATE_OPTIONAL_TIME_NANOS);
            query = node(VALUE_BOOL, node(VALUE_FILTER, Arrays.asList(query, node(VALUE_RANGE, node(field, bounds)))));
        }
        return query;
    }

    /**
     * 聚合 filter 不添加全局日期边界，由外层请求统一追加。
     */
    public Map<String, Object> condition(QueryCondition value, ResolvedIndex target, Map<String, FieldMetadata> fields) {
        return value == null ? node(VALUE_MATCH_ALL, Collections.emptyMap()) : condition(value, target, fields, 0, new int[]{0});
    }

    private Map<String, Object> condition(QueryCondition value, ResolvedIndex target, Map<String, FieldMetadata> fields, int depth, int[] nodes) {
        if (value == null || depth > properties.getQueryLimits().getMaxDepth() || ++nodes[0] > properties.getQueryLimits().getMaxNodes())
            throw invalid();
        if (value.getConditions() != null && !value.getConditions().isEmpty()) {
            if (text(value.getField()) || text(value.getOp()) || value.getValue() != null || (value.getValues() != null && !value.getValues().isEmpty()))
                throw invalid();
            String logic = value.getLogic() == null ? VALUE_AND : value.getLogic();
            if (!Arrays.asList(VALUE_AND, VALUE_OR, VALUE_NOT).contains(logic)
                    || VALUE_NOT.equals(logic) && value.getConditions().size() != 1) throw invalid();
            List<Object> items = new ArrayList<>();
            for (QueryCondition child : value.getConditions())
                items.add(condition(child, target, fields, depth + 1, nodes));
            Map<String, Object> body = new LinkedHashMap<>();
            // NOT 必须编译为真正的补集；反转比较符会漏掉缺失字段并误判多值字段。
            body.put(VALUE_NOT.equals(logic) ? VALUE_MUST_NOT : VALUE_OR.equals(logic) ? VALUE_SHOULD : VALUE_FILTER, items);
            if (VALUE_OR.equals(logic)) body.put(VALUE_MINIMUM_SHOULD_MATCH, 1);
            return node(VALUE_BOOL, body);
        }
        if (text(value.getLogic())) throw invalid();
        QueryOperator op;
        try {
            op = value.getOperatorEnum();
        } catch (RuntimeException error) {
            throw invalid();
        }
        if (op == null || (op.needsValue() && !op.needsMultipleValues() && value.getValue() == null)
                || (op.needsMultipleValues() && (value.getValues() == null || value.getValues().isEmpty()))
                || (value.getValues() != null && value.getValues().size() > properties.getQueryLimits().getMaxNodes()))
            throw invalid();
        if (value.getValue() instanceof Number && !Double.isFinite(((Number) value.getValue()).doubleValue()))
            throw invalid();
        if (op.needsMultipleValues()) for (Object item : value.getValues())
            if (item == null || item instanceof Map || item instanceof Collection || item instanceof Number && !Double.isFinite(((Number) item).doubleValue()))
                throw invalid();
        if (value.getValue() instanceof Map || value.getValue() instanceof Collection) throw invalid();
        String requested = value.getField();
        sensitive.require(target.getConfig(), requested, false);
        boolean id = VALUE_ES_ID.equals(requested);
        if (id) {
            if (!Arrays.asList(QueryOperator.EQ, QueryOperator.NE, QueryOperator.IN, QueryOperator.NOT_IN).contains(op))
                throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
            List<Object> values = op.needsMultipleValues() ? value.getValues() : Collections.singletonList(value.getValue());
            Map<String, Object> result = node(VALUE_IDS, node(VALUE_VALUES, values));
            return op == QueryOperator.NE || op == QueryOperator.NOT_IN ? negative(result) : result;
        }
        String name = field(requested, target, fields, false);
        FieldMetadata metadata = fields.get(requested);
        switch (op) {
            case EQ:
            case NE: {
                Map<String, Object> result = node(VALUE_TEXT.equals(metadata.getType()) && metadata.getKeywordField() == null ? VALUE_MATCH_PHRASE : VALUE_TERM, node(name, value.getValue()));
                return op == QueryOperator.NE ? negative(result) : result;
            }
            case IN:
            case NOT_IN: {
                if (VALUE_TEXT.equals(metadata.getType()) && metadata.getKeywordField() == null)
                    throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
                Map<String, Object> result = node(VALUE_TERMS, node(name, value.getValues()));
                return op == QueryOperator.NOT_IN ? negative(result) : result;
            }
            case GT:
            case GTE:
            case LT:
            case LTE:
                requireRange(metadata);
                return node(VALUE_RANGE, node(name, node(op.getOperator(), value.getValue())));
            case BETWEEN: {
                requireRange(metadata);
                if (value.getValues().size() != RANGE_VALUE_COUNT) throw invalid();
                Map<String, Object> bounds = new LinkedHashMap<>();
                bounds.put(VALUE_GTE, value.getValues().get(0));
                bounds.put(VALUE_LTE, value.getValues().get(1));
                return node(VALUE_RANGE, node(name, bounds));
            }
            case EXISTS:
            case IS_NOT_NULL:
                return node(VALUE_EXISTS, node(VALUE_FIELD, requested));
            case NOT_EXISTS:
            case IS_NULL:
                return negative(node(VALUE_EXISTS, node(VALUE_FIELD, requested)));
            case LIKE:
            case NOT_LIKE:
            case PREFIX:
            case NOT_PREFIX:
            case SUFFIX:
            case NOT_SUFFIX:
            case REGEX:
            case NOT_REGEX: {
                if (!(value.getValue() instanceof String) || !Arrays.asList(VALUE_KEYWORD, VALUE_CONSTANT_KEYWORD, VALUE_WILDCARD, VALUE_TEXT).contains(metadata.getType())
                        || (VALUE_TEXT.equals(metadata.getType()) && metadata.getKeywordField() == null))
                    throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
                String input = (String) value.getValue();
                Map<String, Object> result;
                if (op == QueryOperator.REGEX || op == QueryOperator.NOT_REGEX)
                    result = node(VALUE_REGEXP, node(name, input));
                else if (op == QueryOperator.PREFIX || op == QueryOperator.NOT_PREFIX)
                    result = node(VALUE_PREFIX, node(name, input));
                else
                    result = node(VALUE_WILDCARD, node(name, op == QueryOperator.SUFFIX || op == QueryOperator.NOT_SUFFIX ? WILDCARD_STAR + input : input.contains(WILDCARD_STAR) || input.contains(WILDCARD_SINGLE) ? input : WILDCARD_STAR + input + WILDCARD_STAR));
                return Arrays.asList(QueryOperator.NOT_LIKE, QueryOperator.NOT_PREFIX, QueryOperator.NOT_SUFFIX, QueryOperator.NOT_REGEX).contains(op) ? negative(result) : result;
            }
            default:
                throw invalid();
        }
    }

    /**
     * 校验字段能力并选择有效 keyword 路径。
     */
    public String field(String name, ResolvedIndex target, Map<String, FieldMetadata> fields, boolean sortable) {
        sensitive.require(target.getConfig(), name, sortable);
        FieldMetadata metadata = fields.get(name);
        if (metadata == null || metadata.isConflict() || metadata.isNested() || (!sortable && !metadata.isIndexed())
                || Arrays.asList(VALUE_OBJECT, VALUE_NESTED, VALUE_ALIAS).contains(metadata.getType()))
            throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
        String actual = metadata.getKeywordField() == null ? name : metadata.getKeywordField();
        FieldMetadata actualMetadata = fields.get(actual);
        if (actualMetadata == null || actualMetadata.isConflict() || actualMetadata.isNested() || (!sortable && !actualMetadata.isIndexed()))
            throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
        sensitive.require(target.getConfig(), actual, sortable);
        if (sortable) {
            FieldMetadata resolved = fields.get(actual);
            if (resolved == null || !resolved.isDocValues() || resolved.isConflict())
                throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
            sensitive.require(target.getConfig(), actual, true);
        }
        return actual;
    }

    private void requireRange(FieldMetadata field) {
        if (!Arrays.asList(VALUE_BYTE, VALUE_SHORT, VALUE_INTEGER, VALUE_LONG, VALUE_UNSIGNED_LONG, VALUE_HALF_FLOAT, VALUE_FLOAT, VALUE_DOUBLE, VALUE_SCALED_FLOAT, VALUE_DATE, VALUE_DATE_NANOS, VALUE_IP).contains(field.getType()))
            throw io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.field();
    }

    private Map<String, Object> negative(Map<String, Object> value) {
        return node(VALUE_BOOL, node(VALUE_MUST_NOT, Collections.singletonList(value)));
    }
}
