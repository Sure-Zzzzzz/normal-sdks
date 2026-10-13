package io.github.surezzzzzz.sdk.feedback.server.controller.request;

import lombok.Data;

/**
 * 提交反馈请求（multipart 的 JSON part；owner/快照来自认证上下文不在请求体）。
 *
 * @author surezzzzzz
 */
@Data
public class SubmissionCreateRequest {

    /**
     * 目标应用编码
     */
    private String applicationCode;

    /**
     * 发起形态 code（proactive/intercepted）
     */
    private String kind;

    /**
     * 反馈类型 code（bug/feature/experience/other）
     */
    private String type;

    /**
     * 满意度打分（1-10）
     */
    private Integer score;

    /**
     * 反馈文本
     */
    private String content;

    /**
     * 联系邮箱（选填）
     */
    private String contactEmail;

    /**
     * 发起页面路径（深链预填，可改可清）
     */
    private String contextPath;

    /**
     * 环境快照 JSON 原文（键白名单服务端校验）
     */
    private String environment;
}
