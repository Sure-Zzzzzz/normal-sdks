package io.github.surezzzzzz.sdk.audit.http.xff.listener;

import io.github.surezzzzzz.sdk.audit.http.xff.annotation.SimpleXffCaptureAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.factory.XffCaptureAuditDocumentFactory;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * XFF Capture 事件审计 Listener。
 *
 * <p>Listener 在事件同步线程构造不可变文档，再通过专用执行器广播给全部 Provider。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleXffCaptureAuditListenerComponent
public class XffCaptureAuditEventListener {

    private final List<XffCaptureAuditPersistenceProvider> providers;
    private final XffCaptureAuditDocumentFactory documentFactory;
    private final Executor executor;

    /**
     * 创建 XFF Capture 审计 Listener。
     *
     * @param providers       审计文档 Provider
     * @param documentFactory 审计文档 Factory
     * @param executor        Listener 专用执行器
     */
    public XffCaptureAuditEventListener(List<XffCaptureAuditPersistenceProvider> providers,
                                        XffCaptureAuditDocumentFactory documentFactory,
                                        @Qualifier(SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME)
                                        Executor executor) {
        this.providers = providers;
        this.documentFactory = documentFactory;
        this.executor = executor;
    }

    /**
     * 处理 XFF Capture 事件。
     *
     * @param event Capture 事件
     */
    @EventListener
    public void onXffCaptureEvent(XffCaptureEvent event) {
        if (event == null) {
            return;
        }
        log.debug("XFF 审计事件接收：eventId=[{}]，providerCount=[{}]",
                event.getEventId(), providers.size());

        XffCaptureAuditDocument document;
        try {
            document = documentFactory.create(event);
            log.debug("XFF 审计文档构建完成：eventId=[{}]", document.getEventId());
        } catch (RuntimeException e) {
            log.warn("XFF 审计文档转换失败，eventId=[{}]，异常类型=[{}]",
                    event.getEventId(), e.getClass().getName());
            return;
        }

        try {
            executor.execute(() -> persist(document));
            log.debug("XFF 审计任务已提交：eventId=[{}]", document.getEventId());
        } catch (RejectedExecutionException e) {
            log.warn("XFF 审计队列已满，eventId=[{}]，异常类型=[{}]",
                    document.getEventId(), e.getClass().getName());
        } catch (RuntimeException e) {
            log.warn("XFF 审计任务提交失败，eventId=[{}]，异常类型=[{}]",
                    document.getEventId(), e.getClass().getName());
        }
    }

    private void persist(XffCaptureAuditDocument document) {
        for (XffCaptureAuditPersistenceProvider provider : providers) {
            try {
                provider.persist(document);
                log.debug("XFF 审计 Provider 完成：eventId=[{}]，provider=[{}]",
                        document.getEventId(), provider.getClass().getName());
            } catch (RuntimeException e) {
                log.warn("XFF 审计 Provider 执行失败，eventId=[{}]，provider=[{}]，异常类型=[{}]",
                        document.getEventId(), provider.getClass().getName(), e.getClass().getName());
            }
        }
        log.debug("XFF 审计广播完成：eventId=[{}]，providerCount=[{}]",
                document.getEventId(), providers.size());
    }
}
