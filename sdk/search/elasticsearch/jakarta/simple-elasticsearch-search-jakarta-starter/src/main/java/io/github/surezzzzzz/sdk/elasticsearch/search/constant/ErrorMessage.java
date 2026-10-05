package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 固定错误消息；禁止拼接 DSL、字段值、地址或 ES 原始 reason。
 */
public final class ErrorMessage {
    public static final String REQUEST_INVALID = "查询参数无效或超出限制";
    public static final String CONFIG_INVALID = "查询配置无效或索引归属不明确";
    public static final String FIELD_INVALID = "字段不存在、不可用于该操作或被敏感策略限制";
    public static final String MAPPING_FAILED = "索引字段元数据加载失败";
    public static final String CURSOR_INVALID = "分页游标缺失、无效、过期或与请求不匹配";
    public static final String RESPONSE_INVALID = "Elasticsearch 响应不符合查询契约";
    public static final String REST_FAILED = "Elasticsearch 查询请求失败";

    private ErrorMessage() {
    }
}
