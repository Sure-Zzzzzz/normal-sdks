package io.github.surezzzzzz.sdk.ticket.server.constant;

/**
 * Simple Ticket Server 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleTicketServerConstant {

    private SimpleTicketServerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.ticket.server";

    /**
     * REST 根路径默认值。
     */
    public static final String DEFAULT_API_BASE_PATH = "/api/ticket";

    // ==================== 单号常量 ====================

    /**
     * 单号前缀。
     */
    public static final String TICKET_NO_PREFIX = "TK";

    /**
     * 单号日序列位宽。
     */
    public static final int TICKET_NO_SEQUENCE_WIDTH = 6;

    // ==================== 满意度常量 ====================

    /**
     * 评价星下限。
     */
    public static final int STAR_MIN = 1;

    /**
     * 评价星上限。
     */
    public static final int STAR_MAX = 5;

    // ==================== 字段长度上限（与 Properties 默认值同源） ====================

    /**
     * 标题长度上限。
     */
    public static final int SUBJECT_MAX_LENGTH = 120;

    /**
     * 描述长度上限。
     */
    public static final int DESCRIPTION_MAX_LENGTH = 8000;

    /**
     * 模板字段数上限。
     */
    public static final int TEMPLATE_FIELD_MAX_COUNT = 20;

    /**
     * 模板单字段长度上限。
     */
    public static final int TEMPLATE_FIELD_MAX_LENGTH = 2000;

    /**
     * 模板下拉选项数上限。
     */
    public static final int TEMPLATE_OPTION_MAX_COUNT = 20;

    /**
     * 附件单工单数量上限。
     */
    public static final int ATTACHMENT_MAX_COUNT_PER_TICKET = 10;

    /**
     * 附件大小上限（MB）。
     */
    public static final int ATTACHMENT_MAX_SIZE_MB = 20;

    // ==================== SLA 常量 ====================

    /**
     * 确认超时默认时长（小时，全局统一不分类型）。
     */
    public static final long DEFAULT_CONFIRM_TIMEOUT_HOURS = 168L;

    /**
     * SLA 扫描默认周期（秒）。
     */
    public static final long DEFAULT_SCAN_FIXED_RATE_SECONDS = 60L;


    // ==================== API 权限码（DESIGN §7.4，与 IAM/AKSK 逐字登记） ====================

    /**
     * 类型管理码。
     */
    public static final String PERMISSION_TYPE_ADMIN = "ticket:type-admin";

    /**
     * 处理组管理码。
     */
    public static final String PERMISSION_GROUP_ADMIN = "ticket:group-admin";

    /**
     * 报表码。
     */
    public static final String PERMISSION_REPORT = "ticket:report";

    /**
     * 全量工单页面权限，仅作为全量读和分派/转派的数据身份门。
     */
    public static final String PAGE_ALL = "ticket.page.all";

    /**
     * 工单数据资源标识。
     */
    public static final String DATA_RESOURCE_TICKET = "ticket";

    /**
     * 工单数据读取动作。
     */
    public static final String DATA_ACTION_READ = "read";

    /**
     * 读取码。
     */
    public static final String PERMISSION_READ = "ticket:read";

    /**
     * 创建码。
     */
    public static final String PERMISSION_CREATE = "ticket:create";

    /**
     * 留言码。
     */
    public static final String PERMISSION_COMMENT = "ticket:comment";

    /**
     * 受理码。
     */
    public static final String PERMISSION_CLAIM = "ticket:claim";

    /**
     * 分派码。
     */
    public static final String PERMISSION_ASSIGN = "ticket:assign";

    /**
     * 转派码。
     */
    public static final String PERMISSION_TRANSFER = "ticket:transfer";

    /**
     * 办结码。
     */
    public static final String PERMISSION_RESOLVE = "ticket:resolve";

    /**
     * 关闭码。
     */
    public static final String PERMISSION_CLOSE = "ticket:close";

    /**
     * 撤销码。
     */
    public static final String PERMISSION_WITHDRAW = "ticket:withdraw";

    /**
     * 评价码。
     */
    public static final String PERMISSION_EVALUATE = "ticket:evaluate";
}
