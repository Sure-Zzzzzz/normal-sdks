package io.github.surezzzzzz.sdk.ticket.server.constant;

/**
 * 工单错误码（与 ErrorMessage 一一对应；HTTP 语义见 DESIGN §7.4）。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 工单类型不存在
     */
    public static final String TICKET_TYPE_NOT_FOUND = "TICKET_TYPE_NOT_FOUND";

    /**
     * 工单类型已停用
     */
    public static final String TICKET_TYPE_DISABLED = "TICKET_TYPE_DISABLED";

    /**
     * 处理组不存在
     */
    public static final String TICKET_GROUP_NOT_FOUND = "TICKET_GROUP_NOT_FOUND";

    /**
     * 处理组存在在途单（删除被拒）
     */
    public static final String TICKET_GROUP_HAS_OPEN_TICKETS = "TICKET_GROUP_HAS_OPEN_TICKETS";

    /**
     * 状态冲突（非法转移，409）
     */
    public static final String TICKET_STATE_CONFLICT = "TICKET_STATE_CONFLICT";

    /**
     * 工单不可见（404 掩蔽越权）
     */
    public static final String TICKET_NOT_ACCESSIBLE = "TICKET_NOT_ACCESSIBLE";

    /**
     * 可见但角色不符（403）
     */
    public static final String TICKET_FORBIDDEN_ACTION = "TICKET_FORBIDDEN_ACTION";

    /**
     * 幂等键同键异体（409）
     */
    public static final String TICKET_IDEMPOTENT_CONFLICT = "TICKET_IDEMPOTENT_CONFLICT";

    /**
     * 资源唯一键冲突（409）。
     */
    public static final String TICKET_RESOURCE_CONFLICT = "TICKET_RESOURCE_CONFLICT";

    /**
     * 字段校验失败（400）
     */
    public static final String TICKET_VALIDATION_FAILED = "TICKET_VALIDATION_FAILED";

    /**
     * 类型模板自身不合法（管理面）
     */
    public static final String TICKET_TEMPLATE_INVALID = "TICKET_TEMPLATE_INVALID";

    /**
     * 附件被拒（大小/类型/数量）
     */
    public static final String TICKET_ATTACHMENT_REJECTED = "TICKET_ATTACHMENT_REJECTED";

    /**
     * 附件不存在
     */
    public static final String TICKET_ATTACHMENT_NOT_FOUND = "TICKET_ATTACHMENT_NOT_FOUND";

    /**
     * 工单内部处理失败
     */
    public static final String TICKET_SERVICE_ERROR = "TICKET_SERVICE_ERROR";
}
