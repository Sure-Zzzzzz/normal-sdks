package io.github.surezzzzzz.sdk.auth.resource.server.interceptor;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.spi.ApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ConfiguredApiPermissionResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 精确API权限拦截器。
 *
 * @author surezzzzzz
 */
public final class RequireApiPermissionInterceptor implements HandlerInterceptor {

    private final ApplicationAuthorizationEvaluator evaluator;
    private final ConfiguredApiPermissionResolver resolver;

    /**
     * 创建精确API权限拦截器。
     *
     * @param evaluator 精确API权限判定器
     * @param resolver  配置化精确API权限规则解析器
     */
    public RequireApiPermissionInterceptor(ApplicationAuthorizationEvaluator evaluator,
                                           ConfiguredApiPermissionResolver resolver) {
        this.evaluator = evaluator;
        this.resolver = resolver;
    }

    /**
     * 校验Controller声明的精确API权限。
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  Controller处理器
     * @return 是否允许继续执行
     * @throws Exception 处理异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod) || !resolver.isProtected(request)) {
            return true;
        }
        String apiPermission = resolver.resolve(request);
        if (apiPermission == null) {
            RequireApiPermission permission = findPermission((HandlerMethod) handler);
            if (permission != null) {
                apiPermission = permission.value();
            }
        }
        if (apiPermission == null) {
            response.sendError(SimpleResourceServerStarterConstant.HTTP_STATUS_FORBIDDEN,
                    SimpleResourceServerStarterConstant.MESSAGE_FORBIDDEN);
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof VerifiedResourceContext)) {
            response.sendError(SimpleResourceServerStarterConstant.HTTP_STATUS_FORBIDDEN,
                    SimpleResourceServerStarterConstant.MESSAGE_FORBIDDEN);
            return false;
        }
        VerifiedResourceContext context = (VerifiedResourceContext) authentication.getPrincipal();
        ApplicationAuthorizationDecision decision = evaluator.evaluateApi(context.getApplicationAuthorization(),
                context.getApplicationAuthorization().getApplicationCode(), apiPermission);
        if (decision != ApplicationAuthorizationDecision.ALLOW) {
            response.sendError(SimpleResourceServerStarterConstant.HTTP_STATUS_FORBIDDEN,
                    SimpleResourceServerStarterConstant.MESSAGE_FORBIDDEN);
            return false;
        }
        return true;
    }

    private RequireApiPermission findPermission(HandlerMethod handler) {
        RequireApiPermission methodPermission = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
                RequireApiPermission.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequireApiPermission.class);
    }
}
