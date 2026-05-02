package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.filter;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.support.AkskContextHelper;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.support.HeaderNameConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.configuration.SimpleAkskSecurityContextProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * AKSK 安全上下文过滤器
 *
 * <p>从 HTTP Header 提取用户信息并注入到 Request Attribute。
 *
 * <p>工作流程：
 * <ol>
 *   <li>遍历所有 HTTP Header</li>
 *   <li>筛选出以指定前缀开头的 Header</li>
 *   <li>移除前缀并转换为 camelCase</li>
 *   <li>存储到 Request Attribute</li>
 *   <li>发布 AkskAccessEvent 事件</li>
 * </ol>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class AkskSecurityContextFilter implements Filter {

    private final SimpleAkskSecurityContextProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public AkskSecurityContextFilter(SimpleAkskSecurityContextProperties properties,
                                     ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        Map<String, String> context = extractSecurityContext(httpRequest);

        if (!context.isEmpty()) {
            httpRequest.setAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE, context);
            log.debug("Security context injected: {} fields", context.size());

            try {
                eventPublisher.publishEvent(
                        AkskContextHelper.buildAccessEvent(
                                this, SimpleAkskResourceConstant.ACCESS_SOURCE_HEADER, context, httpRequest));
            } catch (Exception e) {
                log.warn("Failed to publish AkskAccessEvent", e);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 从 HTTP Header 提取安全上下文
     *
     * @param request HTTP 请求
     * @return 安全上下文 Map（camelCase 格式的字段名）
     */
    private Map<String, String> extractSecurityContext(HttpServletRequest request) {
        Map<String, String> context = new HashMap<>();
        String prefix = properties.getHeaderPrefix();

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return context;
        }

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headerName != null && headerName.startsWith(prefix)) {
                String headerValue = request.getHeader(headerName);
                if (headerValue != null && !headerValue.isEmpty()) {
                    String camelCaseKey = HeaderNameConverter.toCamelCase(headerName, prefix);
                    context.put(camelCaseKey, headerValue);
                    log.trace("Extracted header: {} -> {} = {}", headerName, camelCaseKey, headerValue);
                }
            }
        }

        return context;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("AkskSecurityContextFilter initialized with prefix: {}", properties.getHeaderPrefix());
    }

    @Override
    public void destroy() {
        log.info("AkskSecurityContextFilter destroyed");
    }
}
