package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析后的索引配置
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedIndexConfig {

    /**
     * 请求传入的索引
     */
    private String requestIndex;

    /**
     * 命中的配置索引
     */
    private String configIndex;

    /**
     * 配置标识，alias 优先
     */
    private String configIdentifier;

    /**
     * 命中的索引配置
     */
    private SimpleElasticsearchSearchProperties.IndexConfig indexConfig;

    /**
     * 是否通过 wildcard name 命中
     */
    private boolean wildcardMatched;
}
