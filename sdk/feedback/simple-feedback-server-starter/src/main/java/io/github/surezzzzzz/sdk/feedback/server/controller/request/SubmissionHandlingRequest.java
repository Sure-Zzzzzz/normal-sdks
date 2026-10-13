package io.github.surezzzzzz.sdk.feedback.server.controller.request;

import lombok.Data;

/**
 * 处理反馈请求（管理侧；行版本字段派并发控制，409 语义对浏览器直白）。
 *
 * @author surezzzzzz
 */
@Data
public class SubmissionHandlingRequest {

    /**
     * 目标状态 code（submitted/resolved/dismissed）
     */
    private String targetStatus;

    /**
     * 内部备注（整体覆盖，可空）
     */
    private String handlerNote;

    /**
     * 给提交人的答复（可空；非空时提交人可见）
     */
    private String reply;

    /**
     * 期望行版本（乐观锁）
     */
    private Integer expectedRowVersion;
}
