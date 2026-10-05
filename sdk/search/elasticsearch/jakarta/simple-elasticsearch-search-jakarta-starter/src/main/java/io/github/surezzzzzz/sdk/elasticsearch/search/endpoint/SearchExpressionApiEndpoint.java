package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionValidationRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;

/**
 * 复用宿主认证和查询门面；校验原文放请求体，不放可被访问日志记录的 URL。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SimpleElasticsearchSearchConstant.CONFIG_PREFIX, name = {VALUE_ENABLE, VALUE_API_ENABLED}, havingValue = VALUE_TRUE)
@RequestMapping("${io.github.surezzzzzz.sdk.elasticsearch.search.api.base-path:/api}")
public class SearchExpressionApiEndpoint {
    private final ExpressionService expressions;

    /**
     * 表达式查询返回既有顶层结果，不添加统一包装。
     */
    @PostMapping("/query/expression")
    public QueryResponse query(@RequestBody ExpressionQueryRequest request) {
        return expressions.query(request);
    }

    /**
     * 表达式过滤后的聚合复用既有聚合定义。
     */
    @PostMapping("/agg/expression")
    public AggResponse aggregate(@RequestBody ExpressionAggRequest request) {
        return expressions.aggregate(request);
    }

    /**
     * 校验为正常业务结果；外部依赖失败不返回 valid=false。
     */
    @PostMapping("/expression/validation")
    public ExpressionValidationResponse validate(@RequestBody ExpressionValidationRequest request) {
        return expressions.validate(request);
    }

    /**
     * 只提供当前允许目录的字段及语法提示。
     */
    @GetMapping("/expression/hints")
    public ExpressionHintsResponse hints(@RequestParam String index) {
        return expressions.hints(index);
    }
}
