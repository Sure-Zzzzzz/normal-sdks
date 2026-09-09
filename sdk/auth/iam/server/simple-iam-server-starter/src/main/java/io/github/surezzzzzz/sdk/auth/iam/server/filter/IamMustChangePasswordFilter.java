package io.github.surezzzzzz.sdk.auth.iam.server.filter;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 须改密强制拦截器。
 *
 * <p>管理员建号 / 重置密码后 {@code iam_user.must_change_password=1}，用户完成
 * 自助改密前，除登录 / 改密 / 登出等自身流程外的一切 API 均拒绝（403 +
 * {@code AUTH_009}），防止临时密码长期滞留。标记快照由
 * {@link IamSessionValidationFilter} 每请求同步，改密完成的下一请求即解除。</p>
 *
 * <p>仅拦持有 IAM 登录态的会话（attribute 快照为 TRUE 时）；匿名与未标记请求
 * 零成本放行。挂授权服务器 / web / admin / iam app 四链（机器验证链不挂）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@ConditionalOnProperty(prefix = SimpleIamServerConstant.CONFIG_PREFIX + ".password",
        name = "must-change-enforcement", matchIfMissing = true)
@RequiredArgsConstructor
public class IamMustChangePasswordFilter extends OncePerRequestFilter {

    /**
     * 放行前缀：认证自身流程（取码 / 登录 / 登录方式 / 验证码 / SSO 发起与回调）+
     * 改密 + 登出 + 自身信息（改密页展示用户名）。
     *
     * <p>授权服务器端点（{@code /oauth2/**}）不在白名单：须改密用户发起授权码
     * 流程被 403 引导先完成改密，改密后由登录页 redirect 参数自然重发起——
     * token 兑换端点本身无需会话，防线针对的是浏览器授权步骤。</p>
     */
    private static final List<String> ALLOWED_PATH_PREFIXES = Arrays.asList(
            "/iam/web/auth/csrf",
            "/iam/web/auth/login",
            "/iam/web/auth/providers",
            "/iam/web/auth/captcha",
            "/iam/web/auth/password",
            "/iam/web/auth/logout",
            "/iam/web/auth/me",
            "/iam/web/auth/authorize/",
            "/iam/web/auth/callback/");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        boolean mustChange = session != null && Boolean.TRUE.equals(session.getAttribute(
                SimpleIamServerConstant.SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD));
        if (!mustChange || HttpMethod.OPTIONS.matches(request.getMethod()) || isAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(
                request.getContextPath().length());
        for (String prefix : ALLOWED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 403 出口须自写响应体（servlet filter 先于 DispatcherServlet，@RestControllerAdvice
     * 拦不到）；结构对齐全局异常出口并额外携带 code，前端据 code 引导进改密流程。
     */
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("须改密拦截：uri={}，改密完成前拒绝访问", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"code\":\"" + ErrorCode.MUST_CHANGE_PASSWORD
                + "\",\"message\":\"" + ServerErrorMessage.MUST_CHANGE_PASSWORD + "\"}");
    }
}
