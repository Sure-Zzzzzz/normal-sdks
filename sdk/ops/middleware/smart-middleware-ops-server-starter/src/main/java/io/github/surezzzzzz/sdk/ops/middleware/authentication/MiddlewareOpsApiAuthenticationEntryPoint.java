package io.github.surezzzzzz.sdk.ops.middleware.authentication;

import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;

/**
 * Middleware Ops API 未认证安全响应入口。
 *
 * @author surezzzzzz
 */
public class MiddlewareOpsApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * 输出 JSON 401 响应。
     *
     * @param request       HTTP 请求
     * @param response      HTTP 响应
     * @param authException 认证异常
     * @throws IOException      输出失败
     * @throws ServletException Servlet 处理失败
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        Object requestId = request.getAttribute(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(SmartMiddlewareOpsServerConstant.MEDIA_TYPE_APPLICATION_JSON);
        response.getWriter().write(String.format(SmartMiddlewareOpsServerConstant.API_UNAUTHENTICATED_RESPONSE_TEMPLATE,
                SmartMiddlewareOpsServerConstant.API_UNAUTHENTICATED_MESSAGE, Instant.now().toString(),
                requestId == null ? SmartMiddlewareOpsServerConstant.EMPTY_VALUE : requestId.toString()));
    }
}
