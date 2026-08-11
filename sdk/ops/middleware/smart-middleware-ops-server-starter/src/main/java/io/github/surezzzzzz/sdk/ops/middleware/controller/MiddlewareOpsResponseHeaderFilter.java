package io.github.surezzzzzz.sdk.ops.middleware.controller;

import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 为运维 HTTP 响应统一写入 requestId 与禁止缓存指令。
 *
 * @author surezzzzzz
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MiddlewareOpsResponseHeaderFilter extends OncePerRequestFilter {

    private final String apiBasePath;
    private final String uiBasePath;

    /**
     * 创建运维响应头过滤器。
     *
     * @param properties 运维服务配置
     */
    public MiddlewareOpsResponseHeaderFilter(SmartMiddlewareOpsServerProperties properties) {
        this.apiBasePath = properties.getApiBasePath();
        this.uiBasePath = properties.getUiBasePath();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return !(matches(requestPath, apiBasePath) || matches(requestPath, uiBasePath));
    }

    private boolean matches(String requestPath, String path) {
        return requestPath.equals(path) || requestPath.startsWith(path + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER, requestId);
        response.setHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER, requestId);
        response.setHeader("Cache-Control", SmartMiddlewareOpsServerConstant.CACHE_CONTROL_NO_STORE);
        filterChain.doFilter(request, response);
    }
}
