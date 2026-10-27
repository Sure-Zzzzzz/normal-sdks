package io.github.surezzzzzz.sdk.ticket.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单类型聚合（ticket_type 表）。SLA 配置与模板提交时固化，变更不重算存量单（DESIGN §6.1）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketType {

    private Long id;

    /**
     * 类型编码（consult/fault/permission/data/suggestion 预置，可增）
     */
    private String code;

    /**
     * 类型名称
     */
    private String name;

    /**
     * 表单模板 JSON（字段定义数组，校验在 starter 层按模板执行）
     */
    private String formTemplate;

    /**
     * 默认处理组编码（提交时路由）
     */
    private String defaultGroup;

    /**
     * 响应时限分钟（SUBMITTED→受理）
     */
    private Long slaResponseMinutes;

    /**
     * 办结时限分钟（PENDING_INFO 暂停计时）
     */
    private Long slaResolutionMinutes;

    /**
     * 启用状态（停用拒新提交、不碍存量）
     */
    private boolean enabled;
}
