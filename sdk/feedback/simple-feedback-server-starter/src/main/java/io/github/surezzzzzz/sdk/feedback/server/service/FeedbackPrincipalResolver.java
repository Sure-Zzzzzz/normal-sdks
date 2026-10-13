package io.github.surezzzzzz.sdk.feedback.server.service;

import javax.servlet.http.HttpServletRequest;

/**
 * 已认证 HTTP 请求的反馈主体解析端口（聚散边界，DESIGN §4.1/修正⑪）。
 *
 * <p>实现只能从已验证的认证上下文派生 subjectId 与 owner，不能信任请求业务字段。
 * 桥与身份协作件双双缺席时反馈敏感 HTTP API 不得启动（无匿名回退）。</p>
 *
 * @author surezzzzzz
 */
public interface FeedbackPrincipalResolver {

    /**
     * 解析已认证请求的反馈上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 已认证反馈请求上下文；无法派生唯一人员主体时返回 null（走 401 链，不猜测不回退）
     */
    FeedbackRequestContext resolve(HttpServletRequest request);
}
