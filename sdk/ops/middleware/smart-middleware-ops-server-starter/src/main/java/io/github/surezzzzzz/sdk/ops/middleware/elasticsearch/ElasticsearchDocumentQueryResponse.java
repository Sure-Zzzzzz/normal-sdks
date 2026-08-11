package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 当前请求的受限文档响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchDocumentQueryResponse {

    /**
     * 当前页码。
     */
    private final int page;
    /**
     * 当前页窗口大小。
     */
    private final int size;
    /**
     * 本次响应命中。
     */
    private final List<Hit> items;
    /**
     * 是否还有下一页。
     */
    private final Boolean hasMore;

    /**
     * 单个命中的安全投影。
     *
     * @author surezzzzzz
     */
    @Getter
    @Builder
    public static class Hit {

        /**
         * 文档索引。
         */
        private final String index;
        /**
         * 文档标识。
         */
        private final String id;
        /**
         * 当前命中 source。
         */
        private final Map<String, Object> source;
    }
}
