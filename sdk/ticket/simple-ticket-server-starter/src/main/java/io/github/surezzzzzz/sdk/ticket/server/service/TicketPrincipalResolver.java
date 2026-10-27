package io.github.surezzzzzz.sdk.ticket.server.service;

import javax.servlet.http.HttpServletRequest;

/**
 * 已认证 HTTP 请求的工单主体解析端口（聚散边界，DESIGN §5.3）。
 *
 * <p>实现只能从已验证认证上下文派生 subjectId 与管理码；桥与身份协作件双双缺席时
 * 工单 HTTP API 不得启动（无匿名回退）。</p>
 *
 * @author surezzzzzz
 */
public interface TicketPrincipalResolver {

    /**
     * 解析已认证请求的工单上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 上下文；无法派生唯一人员主体时返回 null（走 401 链，不猜测不回退）
     */
    TicketRequestContext resolve(HttpServletRequest request);
}
