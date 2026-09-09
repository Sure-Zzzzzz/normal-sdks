package io.github.surezzzzzz.sdk.auth.iam.server.filter;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 会话有效性校验 + 登录态权限热刷新。
 *
 * <p>每次请求校验 servlet 会话绑定的 IAM 会话是否仍然有效；有效则进一步比对
 * 会话内权限版本快照与 iam_user.permission_version，不一致（或旧登录态缺失快照）
 * 时重载用户权限并就地替换登录态——角色/权限变更对在线用户下一请求生效，
 * 全程无需重新登录。同一读库点顺带同步须改密标记快照：管理员重置他人密码后，
 * 该用户在线会话下一请求即被标记，锁进强制改密流程。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamSessionValidationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final IamUserRepository userRepository;
    private final IamUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession servletSession = request.getSession(false);
        if (isIamUserAuthentication(authentication) && servletSession != null) {
            IamSessionEntity iamSession = sessionService.getActiveBinding(servletSession);
            if (iamSession == null || !iamSession.getUsername().equals(authentication.getName())) {
                invalidateSession(servletSession);
            } else {
                refreshPermissionsIfNeeded(servletSession, iamSession, authentication);
                try {
                    sessionService.touchIfNeeded(iamSession);
                } catch (Exception exception) {
                    log.warn("会话续期失败，按旧有效期继续：sessionId={}", iamSession.getId(), exception);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 权限版本不一致时重载登录态权限；刷新失败降级保留旧权限，下一请求重试。
     */
    private void refreshPermissionsIfNeeded(HttpSession servletSession, IamSessionEntity iamSession,
                                            Authentication authentication) {
        Long currentVersion;
        try {
            IamUserEntity user = userRepository.findById(iamSession.getUserId()).orElse(null);
            if (user == null) {
                invalidateSession(servletSession);
                return;
            }
            currentVersion = user.getPermissionVersion() == null ? 0L : user.getPermissionVersion();
            syncMustChangePassword(servletSession, user);
        } catch (Exception exception) {
            log.warn("权限版本读取失败，本次请求沿用旧权限：sessionId={}", iamSession.getId(), exception);
            return;
        }
        Object snapshotVersion = servletSession.getAttribute(
                SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION);
        if (snapshotVersion instanceof Long && currentVersion.equals(snapshotVersion)) {
            return;
        }
        try {
            UserDetails freshDetails = userDetailsService.loadUserByUsername(iamSession.getUsername());
            Authentication renewed = new UsernamePasswordAuthenticationToken(
                    freshDetails, null, freshDetails.getAuthorities());
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(renewed);
            servletSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            servletSession.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION, currentVersion);
            log.debug("登录态权限已热刷新：userId={}, permissionVersion={}", iamSession.getUserId(), currentVersion);
        } catch (Exception exception) {
            log.warn("权限热刷新失败，本次请求沿用旧权限：userId={}", iamSession.getUserId(), exception);
        }
    }

    /**
     * 同步须改密标记快照（值变化才写，避免每请求重写 session 触发序列化）；
     * 读取失败不影响本请求放行——标记与权限版本共用同一 try 块降级。
     */
    private void syncMustChangePassword(HttpSession servletSession, IamUserEntity user) {
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        Object current = servletSession.getAttribute(
                SimpleIamServerConstant.SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD);
        if (current instanceof Boolean && ((Boolean) current) == mustChange) {
            return;
        }
        servletSession.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD, mustChange);
    }

    private void invalidateSession(HttpSession servletSession) {
        servletSession.removeAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_IAM_SESSION_ID);
        servletSession.removeAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION);
        servletSession.removeAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD);
        servletSession.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContextHolder.clearContext();
    }

    private boolean isIamUserAuthentication(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && (authentication.getPrincipal() instanceof IamUserEntity
                || authentication.getPrincipal() instanceof IamUserDetailsSupport);
    }
}
