package io.github.surezzzzzz.sdk.elasticsearch.search.metadata;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;

import java.util.List;

/**
 * Mapping 管理器接口
 *
 * @author surezzzzzz
 */
public interface MappingManager {

    /**
     * 获取索引元数据
     *
     * @param indexAlias 索引别名
     * @return 索引元数据
     */
    IndexMetadata getMetadata(String indexAlias);

    /**
     * 获取索引元数据（支持指定具体索引）
     *
     * @param indexAlias       索引别名
     * @param specificIndexName 具体索引名称（可选，用于通配符索引）
     * @return 索引元数据
     */
    IndexMetadata getMetadata(String indexAlias, String specificIndexName);

    /**
     * 刷新指定索引的 mapping
     *
     * @param indexAlias 索引别名
     */
    void refreshMetadata(String indexAlias);

    /**
     * 刷新所有索引的 mapping
     */
    void refreshAllMetadata();

    /**
     * 获取所有已配置的索引
     *
     * @return 索引配置列表
     */
    List<SimpleElasticsearchSearchProperties.IndexConfig> getAllIndices();

    /**
     * 清除缓存
     */
    void clearCache();
}
