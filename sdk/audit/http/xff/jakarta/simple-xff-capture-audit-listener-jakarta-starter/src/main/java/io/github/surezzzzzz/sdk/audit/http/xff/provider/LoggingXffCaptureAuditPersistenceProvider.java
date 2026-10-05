package io.github.surezzzzzz.sdk.audit.http.xff.provider;

import io.github.surezzzzzz.sdk.audit.http.xff.annotation.SimpleXffCaptureAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import lombok.extern.slf4j.Slf4j;

/**
 * XFF Capture 审计默认日志 Provider。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleXffCaptureAuditListenerComponent
public class LoggingXffCaptureAuditPersistenceProvider implements XffCaptureAuditPersistenceProvider {

    /**
     * 输出一条受控审计摘要。
     *
     * @param document 审计文档
     */
    @Override
    public void persist(XffCaptureAuditDocument document) {
        log.info("XFF 审计事件已投影：eventId=[{}]，applicationName=[{}]，requestId=[{}]，"
                        + "xffPresent=[{}]，xffIpCount=[{}]，publicIpCount=[{}]",
                document.getEventId(), document.getApplicationName(), document.getRequestId(),
                document.isXffPresent(), document.getXffIpList().size(), document.getPublicIpList().size());
    }
}
