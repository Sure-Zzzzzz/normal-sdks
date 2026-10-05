package io.github.surezzzzzz.sdk.elasticsearch.search.expression;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionValidationRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;

import java.time.Instant;

/**
 * 可替换的表达式适配门面；执行仍进入宿主实际装配的 SearchEngine。
 */
public interface ExpressionService {
    /**
     * 默认采用索引配置时区和当前时刻；返回可复用的固定结构化条件。
     */
    QueryCondition translate(String expression, String index);

    /**
     * 显式指定滚动边界、时区与锚点，禁止在值文本中替换字段标签。
     */
    QueryCondition translate(String expression, String index, TimeRangeEnd end, String zone, Instant anchor);

    /**
     * 执行表达式查询或精确计数，复用既有字段和分页保护。
     */
    QueryResponse query(ExpressionQueryRequest request);

    /**
     * 执行带表达式过滤的聚合。
     */
    AggResponse aggregate(ExpressionAggRequest request);

    /**
     * 校验语法、索引和字段能力；真实依赖失败不能冒充无效表达式。
     */
    ExpressionValidationResponse validate(ExpressionValidationRequest request);

    /**
     * 返回可见且可过滤的字段、语法操作符及时间关键字。
     */
    ExpressionHintsResponse hints(String index);
}
