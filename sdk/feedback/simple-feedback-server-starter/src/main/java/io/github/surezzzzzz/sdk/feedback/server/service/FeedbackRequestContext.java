package io.github.surezzzzzz.sdk.feedback.server.service;

import lombok.Value;

/**
 * 已认证反馈请求上下文（owner 只从已验证认证上下文派生，永不出现在请求体）。
 *
 * @author surezzzzzz
 */
@Value
public class FeedbackRequestContext {

    /**
     * 提交人 subjectId（裸值）
     */
    String subjectId;

    /**
     * 归属 owner（iam:{subjectId}）
     */
    String owner;
}
