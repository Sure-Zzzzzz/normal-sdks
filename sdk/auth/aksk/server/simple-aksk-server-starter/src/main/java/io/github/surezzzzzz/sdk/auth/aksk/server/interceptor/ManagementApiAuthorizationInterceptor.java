package io.github.surezzzzzz.sdk.auth.aksk.server.interceptor;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ManagementApiAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.DefaultApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理 REST API 授权拦截器。
 *
 * @author surezzzzzz
 */
@SimpleAkskServerComponent
public class ManagementApiAuthorizationInterceptor implements HandlerInterceptor {

    private final DefaultApplicationAuthorizationEvaluator evaluator = new DefaultApplicationAuthorizationEvaluator();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        ManagementOperation operation = resolveOperation(request);
        if (operation == null) {
            return deny(response);
        }
        ApplicationAuthorizationContext authorization = currentAuthorization();
        if (authorization == null || evaluator.evaluateApi(authorization,
                SimpleAkskServerConstant.MANAGEMENT_APPLICATION_CODE,
                operation.permission) != ApplicationAuthorizationDecision.ALLOW) {
            return deny(response);
        }
        DataAccessPlan plan = authorization.getDataGrantDocument() == null
                ? DataAccessPlan.deny()
                : DataAccessPlan.evaluate(authorization.getDataGrantDocument(),
                new DataPermissionRequest(operation.resource, operation.action));
        if (plan.getOutcome() == io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome.DENY) {
            return deny(response);
        }
        request.setAttribute(ManagementApiAuthorizationConstant.REQUEST_ATTRIBUTE_DATA_ACCESS_PLAN, plan);
        request.setAttribute(ManagementApiAuthorizationConstant.REQUEST_ATTRIBUTE_APPLICATION_AUTHORIZATION, authorization);
        return true;
    }

    private ApplicationAuthorizationContext currentAuthorization() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return ApplicationAuthorizationContextClaimMapper.fromClaim(
                    jwt.getClaim(SimpleAkskServerConstant.JWT_CLAIM_APPLICATION_AUTHORIZATION));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean deny(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }

    private ManagementOperation resolveOperation(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        if (path.startsWith("/api/client")) {
            if (HttpMethod.POST.matches(method)) {
                return client(SimpleAkskServerConstant.MANAGEMENT_ACTION_CREATE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_CREATE);
            }
            if (HttpMethod.GET.matches(method)) {
                return client(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ);
            }
            if (HttpMethod.DELETE.matches(method)) {
                return client(SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_DELETE);
            }
            if (HttpMethod.PATCH.matches(method) || HttpMethod.PUT.matches(method)) {
                return client(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_UPDATE);
            }
            return null;
        }
        if (path.startsWith("/api/application-authorization")) {
            if (HttpMethod.POST.matches(method) && path.endsWith("/revoke")) {
                return applicationAuthorization(SimpleAkskServerConstant.MANAGEMENT_ACTION_REVOKE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_REVOKE);
            }
            if (HttpMethod.POST.matches(method)) {
                return applicationAuthorization(SimpleAkskServerConstant.MANAGEMENT_ACTION_CREATE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_CREATE);
            }
            if (HttpMethod.GET.matches(method)) {
                return applicationAuthorization(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ);
            }
            if (HttpMethod.PUT.matches(method)) {
                return applicationAuthorization(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_UPDATE);
            }
            return null;
        }
        if (path.startsWith("/api/token")) {
            if (HttpMethod.GET.matches(method)) {
                return token(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ);
            }
            if (HttpMethod.POST.matches(method)) {
                return token(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
            }
            if (HttpMethod.DELETE.matches(method)) {
                if ("/api/token".equals(path) && request.getParameter("clientId") != null) {
                    return token(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                            SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
                }
                return token(SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_DELETE);
            }
        }
        return null;
    }

    private ManagementOperation client(String action, String permission) {
        return new ManagementOperation(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT, action, permission);
    }

    private ManagementOperation applicationAuthorization(String action, String permission) {
        return new ManagementOperation(
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
                action, permission);
    }

    private ManagementOperation token(String action, String permission) {
        return new ManagementOperation(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN, action, permission);
    }

    private static final class ManagementOperation {
        private final String resource;
        private final String action;
        private final String permission;

        private ManagementOperation(String resource, String action, String permission) {
            this.resource = resource;
            this.action = action;
            this.permission = permission;
        }
    }
}
