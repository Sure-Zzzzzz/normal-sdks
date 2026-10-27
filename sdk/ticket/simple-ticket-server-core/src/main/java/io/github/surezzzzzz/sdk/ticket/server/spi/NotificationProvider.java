package io.github.surezzzzzz.sdk.ticket.server.spi;

import java.util.List;

/**
 * 通知出口 SPI（DESIGN §5.2）。默认 Noop=日志；IAM 站内信适配待客户端三件套后另立 adaptor。
 *
 * <p>实现必须容错：失败重试与告警由实现侧承担，工单主流程不因通知失败阻塞。</p>
 *
 * @author surezzzzzz
 */
public interface NotificationProvider {

    /**
     * 投递通知（title 含单号+动作，content 含标题摘要）。
     *
     * @param subjectIds 收件人标识列表（离职失效由实现侧按发送失败处理）
     * @param title      标题
     * @param content    正文
     */
    void notify(List<String> subjectIds, String title, String content);
}
