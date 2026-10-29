package io.github.surezzzzzz.sdk.crm.client.constant;

/**
 * CRM 客户端公共响应契约常量。
 *
 * @author surezzzzzz
 */
public final class CrmClientConstant {

    /**
     * HTTP 成功响应的协议校验状态。
     */
    public static final int SUCCESS_HTTP_STATUS = 200;
    /**
     * 列表行数组字段。
     */
    public static final String FIELD_ITEMS = "items";
    /**
     * 机器续读游标字段。
     */
    public static final String FIELD_NEXT_CURSOR = "nextCursor";

    private CrmClientConstant() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS);
    }
}
