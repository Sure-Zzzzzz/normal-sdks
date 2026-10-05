package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties.IndexConfig;
import lombok.Value;

/**
 * 单次读取的固定范围，分页续页复用同一日期边界与物理索引。
 */
@Value
public class ResolvedIndex {
    IndexConfig config;
    String identifier;
    String[] indices;
    String datasource;
    String from, to;
    int downgradeLevel;
}
