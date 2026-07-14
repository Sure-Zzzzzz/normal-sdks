package io.github.surezzzzzz.sdk.elasticsearch.route.model;

import lombok.Builder;
import lombok.Getter;

/**
 * by-query low-level 请求选项
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ByQueryRequestOptions {

    /**
     * 是否等待任务完成
     */
    private final Boolean waitForCompletion;

    /**
     * 完成后是否刷新
     */
    private final Boolean refresh;

    /**
     * 超时时间，单位毫秒
     */
    private final Long timeoutMs;

    /**
     * 并行切片数
     */
    private final Integer slices;

    /**
     * 版本冲突策略
     */
    private final String conflicts;

    /**
     * 每批滚动查询数量
     */
    private final Integer scrollSize;

    /**
     * 每秒请求数节流参数，null 表示不限制
     *
     * <p>ES 默认不限制（传 -1 或不传）。可设正数如 500，或 0 暂停。</p>
     */
    private final Float requestsPerSecond;

    /**
     * 限制处理的最大文档数，超出后任务停止
     */
    private final Long maxDocs;

    /**
     * 开始前要求的活跃分片数
     */
    private final Integer waitForActiveShards;

    /**
     * 路由参数，将操作路由到指定分片
     */
    private final String routing;
}
