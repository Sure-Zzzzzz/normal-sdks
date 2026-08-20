package io.github.surezzzzzz.sdk.audit.http.xff.factory;

import io.github.surezzzzzz.sdk.audit.http.xff.annotation.SimpleXffCaptureAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.configuration.SimpleXffCaptureAuditListenerProperties;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContextProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.support.XffCaptureAuditApplicationNameHelper;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.constant.XffIpScope;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.model.HeaderValueSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffAddressInfo;
import io.github.surezzzzzz.sdk.http.xff.core.support.XffAddressHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * XFF Capture 审计文档 Factory。
 *
 * <p>Factory 在事件同步线程完成所有上下文读取和类型化投影。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleXffCaptureAuditListenerComponent
public class XffCaptureAuditDocumentFactory {

    private final SimpleXffCaptureAuditListenerProperties properties;
    private final Environment environment;
    private final Optional<XffCaptureAuditContextProvider> contextProvider;
    private final DateTimeFormatter capturedTimeFormatter;

    /**
     * 创建审计文档 Factory。
     *
     * @param properties      Listener 配置
     * @param environment     Spring Environment
     * @param contextProvider 可选审计上下文 Provider
     */
    public XffCaptureAuditDocumentFactory(SimpleXffCaptureAuditListenerProperties properties,
                                          Environment environment,
                                          Optional<XffCaptureAuditContextProvider> contextProvider) {
        this.properties = properties;
        this.environment = environment;
        this.contextProvider = contextProvider == null
                ? Optional.<XffCaptureAuditContextProvider>empty() : contextProvider;
        this.capturedTimeFormatter = DateTimeFormatter
                .ofPattern(SimpleXffCaptureAuditListenerConstant.CAPTURED_TIME_FORMAT)
                .withZone(ZoneId.of(SimpleXffCaptureAuditListenerConstant.CAPTURED_TIME_ZONE));
    }

    /**
     * 将 Capture Event 转换为最小审计文档。
     *
     * @param event Capture Event
     * @return 不可变审计文档
     */
    public XffCaptureAuditDocument create(XffCaptureEvent event) {
        XffCaptureAuditContext context = currentContext();
        List<String> xffRawList = event.getXffChain().getRawList();
        Set<String> xffIpSet = new LinkedHashSet<>();
        Set<String> publicIpSet = new LinkedHashSet<>();
        for (String rawValue : xffRawList) {
            XffAddressInfo addressInfo = XffAddressHelper.classify(rawValue);
            if (!addressInfo.isIpLiteral()) {
                continue;
            }
            String normalizedIp = addressInfo.getNormalizedIp();
            xffIpSet.add(normalizedIp);
            if (addressInfo.getScope() == XffIpScope.PUBLIC) {
                publicIpSet.add(normalizedIp);
            }
        }

        XffAddressInfo applicationRemoteAddressInfo = XffAddressHelper.classify(
                event.getApplicationRawRemoteAddress());
        String applicationRemoteIp = applicationRemoteAddressInfo.isIpLiteral()
                ? applicationRemoteAddressInfo.getNormalizedIp() : null;
        HeaderValueSnapshot host = event.getForwardedContext().getHost();

        return new XffCaptureAuditDocument(
                event.getEventId(),
                capturedTimeFormatter.format(event.getOccurredAt()),
                XffCaptureAuditApplicationNameHelper.resolve(properties, environment),
                context == null ? null : context.getRequestId(),
                context == null ? null : context.getTraceId(),
                event.getRequestMethod(),
                event.getRequestUri(),
                host.getRawValueList(),
                event.getXffChain().isPresent(),
                xffRawList,
                new ArrayList<>(xffIpSet),
                new ArrayList<>(publicIpSet),
                event.getApplicationRawRemoteAddress(),
                applicationRemoteIp,
                SimpleXffCaptureCoreConstant.IP_CLASSIFICATION_VERSION,
                context == null ? Collections.<String, String>emptyMap() : context.getExtensions()
        );
    }

    private XffCaptureAuditContext currentContext() {
        if (!contextProvider.isPresent()) {
            return null;
        }
        try {
            return contextProvider.get().currentContext();
        } catch (RuntimeException e) {
            log.warn("XFF 审计上下文读取失败，异常类型=[{}]", e.getClass().getName());
            return null;
        }
    }

}
