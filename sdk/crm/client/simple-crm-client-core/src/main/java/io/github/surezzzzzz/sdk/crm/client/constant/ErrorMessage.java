package io.github.surezzzzzz.sdk.crm.client.constant;

/**
 * CRM 客户端响应校验错误消息，供各传输线共用。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 工具类构造消息。
     */
    public static final String UTILITY_CLASS = "工具类不允许实例化";

    // ==================== 结构和类型校验 ====================
    /**
     * 响应体为空消息。
     */
    public static final String EMPTY_RESPONSE = "响应体为空";
    /**
     * 必填字段缺失消息模板。
     */
    public static final String REQUIRED_FIELD_MISSING = "必填字段缺失: %s";
    /**
     * 顶层未知字段消息模板。
     */
    public static final String UNKNOWN_FIELD = "未知字段: %s";
    /**
     * 状态类型错误消息。
     */
    public static final String STATE_MUST_BE_STRING = "状态字段必须为字符串";
    /**
     * 字符串字段类型错误消息模板。
     */
    public static final String FIELD_MUST_BE_STRING = "字段必须为字符串: %s";
    /**
     * 嵌套资源对象类型错误消息。
     */
    public static final String NESTED_RESOURCE_MUST_BE_OBJECT = "嵌套资源必须为对象";
    /**
     * 嵌套资源集合类型错误消息。
     */
    public static final String NESTED_RESOURCES_MUST_BE_ARRAY = "嵌套资源集合必须为数组";
    /**
     * 列表行数组缺失消息模板。
     */
    public static final String ITEMS_MISSING = "行数组缺失: %s";
    /**
     * 列表封装未知字段消息模板。
     */
    public static final String UNKNOWN_PAGE_FIELD = "未知列表字段: %s";
    /**
     * 游标类型错误消息。
     */
    public static final String CURSOR_MUST_BE_STRING_OR_NULL = "游标必须为非空字符串或 null";
    /**
     * 列表行类型错误消息。
     */
    public static final String ITEM_MUST_BE_OBJECT = "行不是对象";
    private ErrorMessage() {
        throw new UnsupportedOperationException(UTILITY_CLASS);
    }
}
