package io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 站内信发送批次摘要（管理台列表）
 *
 * <p>按 send_batch_id 聚合一次发送产生的全部收件人行；target 四字段解析自发送时的
 * target_summary 快照，是<b>请求目标数</b>（active 过滤前、部门未展开），不等于实际收件人数。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MessageBatchSummaryResponse {

    /**
     * 发送批次ID
     */
    private String sendBatchId;

    /**
     * 标题
     */
    private String title;

    /**
     * 发送人用户名
     */
    private String senderUsername;

    /**
     * 请求指定的用户目标数（非实际收件人数）
     */
    private int targetUserCount;

    /**
     * 请求指定的部门目标数
     */
    private int targetDepartmentCount;

    /**
     * 请求指定的协作组目标数
     */
    private int targetUserGroupCount;

    /**
     * 部门目标是否包含子部门
     */
    private boolean targetIncludeChildDepartments;

    /**
     * 实际收件人数（展开并过滤 active 后）
     */
    private long recipientCount;

    /**
     * 已读人数
     */
    private long readCount;

    /**
     * 发送时间
     */
    private Instant createdAt;
}
