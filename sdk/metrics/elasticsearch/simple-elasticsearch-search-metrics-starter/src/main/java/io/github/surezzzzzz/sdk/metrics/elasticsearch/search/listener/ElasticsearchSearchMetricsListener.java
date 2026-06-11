package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.listener;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.ExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.constant.SimpleElasticsearchSearchMetricsConstant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch Search 指标监听器
 *
 * <p>监听 search-starter 发布的查询/聚合/错误事件，采集 Micrometer 指标。
 *
 * <p>指标列表：
 * <ul>
 *   <li>{@code simple_elasticsearch_search_request_total} — 请求计数（标签：eventType / result / sourceType / downgradeLevel / me）</li>
 *   <li>{@code simple_elasticsearch_search_request_seconds} — 请求耗时，取 ES 服务端 took（标签：eventType / sourceType / downgradeLevel / me）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
public class ElasticsearchSearchMetricsListener {

    private final MeterRegistry registry;

    /**
     * 业务模块标识，由 AutoConfiguration 解析后注入
     */
    private final String me;

    public ElasticsearchSearchMetricsListener(MeterRegistry registry, String me) {
        this.registry = registry;
        this.me = me;
        log.info("ElasticsearchSearchMetricsListener initialized, me={}", me);
    }

    /**
     * 查询成功事件
     */
    @EventListener
    public void onQuerySuccess(EsQueryEvent event) {
        ExecutionContext ctx = event.getContext();
        String sourceType = resolveSourceType(ctx != null ? ctx.getSourceType() : null);
        String downgradeLevel = ctx != null
                ? String.valueOf(ctx.getDowngradeLevel())
                : SimpleElasticsearchSearchMetricsConstant.DOWNGRADE_LEVEL_ZERO;

        incrementCounter(SimpleElasticsearchSearchMetricsConstant.EVENT_TYPE_QUERY,
                SimpleElasticsearchSearchMetricsConstant.RESULT_SUCCESS, sourceType, downgradeLevel);
        recordDuration(SimpleElasticsearchSearchMetricsConstant.EVENT_TYPE_QUERY,
                sourceType, downgradeLevel, event.getResponse());
    }

    /**
     * 聚合成功事件
     */
    @EventListener
    public void onAggSuccess(EsAggEvent event) {
        ExecutionContext ctx = event.getContext();
        String sourceType = resolveSourceType(ctx != null ? ctx.getSourceType() : null);
        String downgradeLevel = ctx != null
                ? String.valueOf(ctx.getDowngradeLevel())
                : SimpleElasticsearchSearchMetricsConstant.DOWNGRADE_LEVEL_ZERO;

        incrementCounter(SimpleElasticsearchSearchMetricsConstant.EVENT_TYPE_AGG,
                SimpleElasticsearchSearchMetricsConstant.RESULT_SUCCESS, sourceType, downgradeLevel);
        recordDuration(SimpleElasticsearchSearchMetricsConstant.EVENT_TYPE_AGG,
                sourceType, downgradeLevel, event.getResponse());
    }

    /**
     * 查询失败事件
     */
    @EventListener
    public void onQueryFailure(EsQueryErrorEvent event) {
        String sourceType = resolveSourceType(event.getSourceType());
        String downgradeLevel = event.getContext() != null
                ? String.valueOf(event.getContext().getDowngradeLevel())
                : SimpleElasticsearchSearchMetricsConstant.DOWNGRADE_LEVEL_ZERO;
        incrementCounter(SimpleElasticsearchSearchMetricsConstant.EVENT_TYPE_QUERY,
                SimpleElasticsearchSearchMetricsConstant.RESULT_FAILURE,
                sourceType,
                downgradeLevel);
    }

    /**
     * 聚合失败事件
     */
    @EventListener
    public void onAggFailure(EsAggErrorEvent event) {
        String sourceType = resolveSourceType(event.getSourceType());
        String downgradeLevel = event.getContext() != null
                ? String.valueOf(event.getContext().getDowngradeLevel())
                : SimpleElasticsearchSearchMetricsConstant.DOWNGRADE_LEVEL_ZERO;
        incrementCounter(SimpleElasticsearchSearchMetricsConstant.EVENT_TYPE_AGG,
                SimpleElasticsearchSearchMetricsConstant.RESULT_FAILURE,
                sourceType,
                downgradeLevel);
    }

    private void incrementCounter(String eventType, String result, String sourceType, String downgradeLevel) {
        Counter.builder(SimpleElasticsearchSearchMetricsConstant.METRIC_REQUEST_TOTAL)
                .tag(SimpleElasticsearchSearchMetricsConstant.TAG_EVENT_TYPE, eventType)
                .tag(SimpleElasticsearchSearchMetricsConstant.TAG_RESULT, result)
                .tag(SimpleElasticsearchSearchMetricsConstant.TAG_SOURCE_TYPE, sourceType)
                .tag(SimpleElasticsearchSearchMetricsConstant.TAG_DOWNGRADE_LEVEL, downgradeLevel)
                .tag(SimpleElasticsearchSearchMetricsConstant.TAG_ME, me)
                .register(registry)
                .increment();
    }

    private void recordDuration(String eventType, String sourceType, String downgradeLevel, Object response) {
        if (response == null) {
            return;
        }
        long tookMs = extractTook(response);
        if (tookMs > 0) {
            Timer.builder(SimpleElasticsearchSearchMetricsConstant.METRIC_REQUEST_SECONDS)
                    .tag(SimpleElasticsearchSearchMetricsConstant.TAG_EVENT_TYPE, eventType)
                    .tag(SimpleElasticsearchSearchMetricsConstant.TAG_SOURCE_TYPE, sourceType)
                    .tag(SimpleElasticsearchSearchMetricsConstant.TAG_DOWNGRADE_LEVEL, downgradeLevel)
                    .tag(SimpleElasticsearchSearchMetricsConstant.TAG_ME, me)
                    .register(registry)
                    .record(tookMs, TimeUnit.MILLISECONDS);
        }
    }

    private long extractTook(Object response) {
        if (response instanceof QueryResponse) {
            Long took = ((QueryResponse) response).getTook();
            return took != null ? took : 0;
        } else if (response instanceof AggResponse) {
            Long took = ((AggResponse) response).getTook();
            return took != null ? took : 0;
        }
        return 0;
    }

    private String resolveSourceType(String sourceType) {
        return sourceType != null
                ? sourceType
                : SimpleElasticsearchSearchMetricsConstant.SOURCE_TYPE_UNKNOWN;
    }
}
