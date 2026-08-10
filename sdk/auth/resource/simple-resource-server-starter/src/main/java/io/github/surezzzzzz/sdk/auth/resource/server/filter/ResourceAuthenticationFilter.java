package io.github.surezzzzzz.sdk.auth.resource.server.filter;

import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.support.ResourceAuthenticationContextHelper;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import io.github.surezzzzzz.sdk.auth.resource.server.support.VerifiedResourceAuthentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * 通用资源认证过滤器。
 *
 * @author surezzzzzz
 */
public final class ResourceAuthenticationFilter extends OncePerRequestFilter {

    private final ResourceServerEngine engine;
    private final Collection<String> protectedPaths;

    /**
     * 创建通用资源认证过滤器。
     *
     * @param engine         认证编排引擎
     * @param protectedPaths 受保护路径
     */
    public ResourceAuthenticationFilter(ResourceServerEngine engine, Collection<String> protectedPaths) {
        this.engine = engine;
        this.protectedPaths = protectedPaths;
    }

    /**
     * 对受保护路径建立资源认证上下文。
     *
     * @param request     HTTP请求
     * @param response    HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet异常
     * @throws IOException      IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        ResourceAuthenticationResult result;
        VerifiedResourceContext context;
        try {
            result = engine.authenticate(request);
            if (result == null || result.getOutcome() != ResourceAuthenticationOutcome.AUTHENTICATED) {
                reject(response);
                return;
            }
            context = ResourceAuthenticationContextHelper.createVerifiedContext(result, UUID.randomUUID().toString());
        } catch (RuntimeException exception) {
            reject(response);
            return;
        }
        try {
            SecurityContextHolder.getContext().setAuthentication(new VerifiedResourceAuthentication(context));
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.sendError(SimpleResourceServerStarterConstant.HTTP_STATUS_UNAUTHORIZED,
                SimpleResourceServerStarterConstant.MESSAGE_UNAUTHORIZED);
    }

    private boolean isProtected(HttpServletRequest request) {
        return ResourceSecurityPathHelper.isProtected(protectedPaths, ResourceSecurityPathHelper.applicationPath(request));
    }
}
