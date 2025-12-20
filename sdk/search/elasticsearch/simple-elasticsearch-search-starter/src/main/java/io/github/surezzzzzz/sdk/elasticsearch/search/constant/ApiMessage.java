package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * API 响应消息常量
 *
 * @author surezzzzzz
 */
public class ApiMessage {

    /**
     * 索引 mapping 刷新成功
     */
    public static final String INDEX_MAPPING_REFRESHED = "Index mapping refreshed successfully";

    /**
     * 所有索引 mapping 刷新成功
     */
    public static final String ALL_INDEX_MAPPINGS_REFRESHED = "All index mappings refreshed successfully";

    private ApiMessage() {
        // 私有构造函数，防止实例化
    }
}
