package io.github.surezzzzzz.sdk.ticket.server.constant;

/**
 * 工单错误消息（与 ErrorCode 一一对应；不含敏感数据，可直达前端）。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 工单类型不存在
     */
    public static final String TICKET_TYPE_NOT_FOUND = "工单类型不存在";

    /**
     * 工单类型已停用
     */
    public static final String TICKET_TYPE_DISABLED = "工单类型已停用，不接受新提交";

    /**
     * 处理组不存在
     */
    public static final String TICKET_GROUP_NOT_FOUND = "处理组不存在";

    /**
     * 处理组存在在途单
     */
    public static final String TICKET_GROUP_HAS_OPEN_TICKETS = "处理组仍有在途工单，只可停用不可删除";

    /**
     * 状态冲突
     */
    public static final String TICKET_STATE_CONFLICT = "当前状态不允许该操作";

    /**
     * 工单不可见
     */
    public static final String TICKET_NOT_ACCESSIBLE = "工单不存在";

    /**
     * 角色不符
     */
    public static final String TICKET_FORBIDDEN_ACTION = "无权执行该操作";

    /**
     * 幂等键同键异体
     */
    public static final String TICKET_IDEMPOTENT_CONFLICT = "幂等键已用于其他请求内容";

    /**
     * 资源唯一键冲突。
     */
    public static final String TICKET_RESOURCE_CONFLICT = "资源标识已存在";

    /**
     * 字段校验失败
     */
    public static final String TICKET_VALIDATION_FAILED = "请求字段校验失败";

    /**
     * 类型模板不合法
     */
    public static final String TICKET_TEMPLATE_INVALID = "工单类型模板不合法";

    /**
     * 附件被拒
     */
    public static final String TICKET_ATTACHMENT_REJECTED = "附件不符合大小、类型或数量限制";

    /**
     * 附件不存在
     */
    public static final String TICKET_ATTACHMENT_NOT_FOUND = "附件不存在";

    /**
     * 工单内部处理失败
     */
    public static final String TICKET_SERVICE_ERROR = "工单服务暂不可用";
}
