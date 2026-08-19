package io.github.surezzzzzz.sdk.http.xff.service;

import io.github.surezzzzzz.sdk.http.xff.annotation.SimpleXffCaptureComponent;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.ForwardedContext;
import io.github.surezzzzzz.sdk.http.xff.core.model.HeaderValueSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffCaptureSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;

/**
 * 默认 XFF 采集服务。
 *
 * <p>服务先写入请求属性再发布事件，确保同一请求最多尝试发布一次。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@SimpleXffCaptureComponent
public class DefaultXffCaptureService implements XffCaptureService {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 采集当前请求中应用可见的完整 XFF 事实，并最多发布一次采集事件。
     *
     * @param request 当前 Servlet 请求，不能为 null
     * @return 不可变 XFF 快照
     */
    @Override
    public XffChain capture(HttpServletRequest request) {
        if (request == null) {
            throw new XffCaptureValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureConstant.FIELD_REQUEST));
        }

        XffCaptureSnapshot snapshot;
        // 同一请求可能被业务并行读取，临界区只负责创建并写入唯一完整快照。
        synchronized (request) {
            Object cached = request.getAttribute(SimpleXffCaptureConstant.REQUEST_ATTRIBUTE_CAPTURE_SNAPSHOT);
            if (cached instanceof XffCaptureSnapshot) {
                return ((XffCaptureSnapshot) cached).getXffChain();
            }
            if (cached != null) {
                log.warn("XFF 请求快照属性类型异常，将重新采集并覆盖");
            }

            snapshot = extractSnapshot(request);
            request.setAttribute(SimpleXffCaptureConstant.REQUEST_ATTRIBUTE_CAPTURE_SNAPSHOT, snapshot);
        }
        // 仅首次创建快照的线程发布，且不在 request monitor 内执行第三方 listener。
        publish(request, snapshot);
        return snapshot.getXffChain();
    }

    /**
     * 一次性采集 XFF 链和固定入口转发 Header，确保事件字段来自同一请求时点。
     */
    private XffCaptureSnapshot extractSnapshot(HttpServletRequest request) {
        HeaderValueSnapshot xffHeader = extractHeader(request,
                SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR);
        List<String> rawList = new ArrayList<>();
        for (String rawHeaderValue : xffHeader.getRawValueList()) {
            split(rawHeaderValue, rawList);
        }
        XffChain xffChain = new XffChain(xffHeader.isPresent(),
                xffHeader.getRawValueList(), rawList);
        ForwardedContext forwardedContext = new ForwardedContext(
                extractHeader(request, SimpleXffCaptureConstant.HEADER_HOST),
                extractHeader(request, SimpleXffCaptureConstant.HEADER_X_REAL_IP),
                extractHeader(request, SimpleXffCaptureConstant.HEADER_X_FORWARDED_HOST),
                extractHeader(request, SimpleXffCaptureConstant.HEADER_X_FORWARDED_PORT),
                extractHeader(request, SimpleXffCaptureConstant.HEADER_X_FORWARDED_PROTO)
        );
        return new XffCaptureSnapshot(xffChain, forwardedContext);
    }

    /**
     * 按大小写不敏感规则收集同名 Header 的全部原始值。
     */
    private HeaderValueSnapshot extractHeader(HttpServletRequest request, String canonicalName) {
        HeaderValueSnapshot standardSnapshot = snapshot(request.getHeaders(canonicalName));
        if (standardSnapshot.isPresent()) {
            return standardSnapshot;
        }

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return standardSnapshot;
        }

        // 标准 API 未读到值时，才兼容大小写敏感的非标准容器或测试实现。
        List<String> rawValueList = new ArrayList<>();
        Set<String> visitedActualNameSet = new LinkedHashSet<>();
        while (headerNames.hasMoreElements()) {
            String actualName = headerNames.nextElement();
            if (actualName == null || !canonicalName.equalsIgnoreCase(actualName)
                    || !visitedActualNameSet.add(actualName)) {
                continue;
            }
            appendValues(request.getHeaders(actualName), rawValueList);
        }
        return new HeaderValueSnapshot(!rawValueList.isEmpty(), rawValueList);
    }

    private HeaderValueSnapshot snapshot(Enumeration<String> values) {
        List<String> rawValueList = new ArrayList<>();
        appendValues(values, rawValueList);
        return new HeaderValueSnapshot(!rawValueList.isEmpty(), rawValueList);
    }

    private void appendValues(Enumeration<String> values, List<String> rawValueList) {
        if (values == null) {
            return;
        }
        while (values.hasMoreElements()) {
            String value = values.nextElement();
            rawValueList.add(value == null ? SimpleXffCaptureCoreConstant.EMPTY_VALUE : value);
        }
    }

    private void split(String rawHeaderValue, List<String> rawList) {
        int start = 0;
        for (int index = 0; index <= rawHeaderValue.length(); index++) {
            if (index == rawHeaderValue.length()
                    || rawHeaderValue.charAt(index) == SimpleXffCaptureConstant.VALUE_SEPARATOR) {
                rawList.add(trimOptionalWhitespace(rawHeaderValue.substring(start, index)));
                start = index + 1;
            }
        }
    }

    private String trimOptionalWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isOptionalWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isOptionalWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean isOptionalWhitespace(char value) {
        return value == SimpleXffCaptureConstant.OPTIONAL_WHITESPACE_SPACE
                || value == SimpleXffCaptureConstant.OPTIONAL_WHITESPACE_TAB;
    }

    private void publish(HttpServletRequest request, XffCaptureSnapshot snapshot) {
        XffCaptureEvent event = new XffCaptureEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                snapshot
        );
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("XFF 采集事件发布失败，eventId=[{}]，异常类型=[{}]",
                    event.getEventId(), e.getClass().getName());
        }
    }
}
