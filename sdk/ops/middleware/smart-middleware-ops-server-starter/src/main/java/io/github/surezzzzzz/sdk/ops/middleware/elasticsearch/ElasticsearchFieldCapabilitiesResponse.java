package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Elasticsearch 字段能力安全目录响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchFieldCapabilitiesResponse {

    private final String datasourceKey;
    private final String index;
    private final List<Item> items;
    private final boolean truncated;

    /**
     * 单个字段的安全能力投影。
     */
    @Getter
    @Builder
    public static class Item {

        private final String name;
        private final List<String> types;
        private final boolean searchable;
        private final boolean aggregatable;
    }
}
