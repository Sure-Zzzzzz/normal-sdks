package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import lombok.Value;

import java.util.List;

/**
 * 配置索引目录，不是统一成功或错误包装。
 */
@Value
public class IndexDirectoryResponse {
    /**
     * 仅包含配置允许的索引条目。
     */
    List<IndexEntry> indices;

    /**
     * 一个允许访问的应用索引标识。
     */
    @Value
    public static class IndexEntry {
        /**
         * 实际索引表达式、应用别名和日期分片标识。
         */
        String name, alias;
        boolean dateSplit;
    }
}
