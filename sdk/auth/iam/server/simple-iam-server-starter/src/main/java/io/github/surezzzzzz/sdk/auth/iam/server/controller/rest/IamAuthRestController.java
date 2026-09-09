package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaChallenge;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request.ChangePasswordRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.request.WebLoginRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response.*;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AuthenticationEventType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import io.github.surezzzzzz.sdk.auth.iam.server.support.ProviderDisplayHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IAM Web 登录态 API
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/web/auth")
@RequiredArgsConstructor
public class IamAuthRestController {

    /**
     * 跳转型登录成功且未携带回跳目标时的默认落点（统一应用门户首页）
     */
    private static final String PORTAL_DEFAULT_TARGET = "/app/";
    private final AuthenticationService authenticationService;
    private final ExternalLoginService externalLoginService;
    private final ExternalProviderRegistry providerRegistry;
    private final ProviderDisplayHelper providerDisplayHelper;
    private final IamUserDetailsService userDetailsService;
    private final UserService userService;
    private final MessageSseService messageSseService;
    private final SessionService sessionService;
    private final CaptchaVerificationSupport captchaVerificationSupport;
    private final ObjectProvider<CaptchaProvider> captchaProviderProvider;
    private final SimpleIamServerProperties properties;
    private final CsrfTokenRepository csrfTokenRepository;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 取 CSRF token（匿名，登录页用）
     */
    @GetMapping("/csrf")
    public ResponseEntity<WebCsrfTokenResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(csrfToken, request, response);
        }
        return ResponseEntity.ok(new WebCsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()));
    }

    /**
     * 登录方式列表（本地密码 + 已装配适配器动态生成）。
     *
     * <p>当前 SPI 体系内凭证校验型适配器即 LDAP（契约 LoginProvider.type 的 ldap 枚举），
     * 故凭证型统一标记 type=ldap；引入第二种凭证源时需在契约中扩展 type 枚举。
     */
    @GetMapping("/providers")
    public ResponseEntity<WebLoginProvidersResponse> providers() {
        List<WebLoginProviderResponse> list = new ArrayList<>();
        list.add(new WebLoginProviderResponse(SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD,
                "账号密码登录", "password", true, null, "使用账号和密码登录"));
        providerRegistry.getCredentialAuthenticators().keySet().forEach(code ->
                list.add(new WebLoginProviderResponse(code, providerDisplayHelper.displayName(code),
                        "ldap", true, null, providerDisplayHelper.displayDescription(code))));
        providerRegistry.getBrowserLoginProviders().forEach((code, provider) ->
                list.add(new WebLoginProviderResponse(code, providerDisplayHelper.displayName(code),
                        "sso", true, "/iam/web/auth/authorize/" + code,
                        providerDisplayHelper.displayDescription(code))));
        return ResponseEntity.ok(new WebLoginProvidersResponse(
                SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD, list));
    }

    /**
     * 人机验证挑战下发（登录页取题用；挑战一次性消费，过期与重取由前端重新调用本端点）。
     */
    @GetMapping("/captcha")
    public ResponseEntity<WebCaptchaResponse> captcha() {
        CaptchaProvider provider = captchaProviderProvider.getIfAvailable();
        if (provider == null) {
            throw new SimpleIamServerException(
                    io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.CAPTCHA_PROVIDER_MISSING,
                    ServerErrorMessage.CAPTCHA_PROVIDER_MISSING);
        }
        CaptchaChallenge challenge = provider.generate();
        return ResponseEntity.ok(new WebCaptchaResponse(
                challenge.getCaptchaId(), challenge.getType(), challenge.getContent()));
    }

    /**
     * 账号密码登录入口：provider 缺省走本地密码，否则交外部身份源登录编排。
     * 凭据校验前先过渐进人机验证判定（达到阈值要求验证码，验证码失败不计密码失败）。
     * 登录成败均发布认证审计事件（携带 provider / IP / User-Agent）。
     */
    @PostMapping("/login")
    public ResponseEntity<WebLoginResponse> login(@RequestBody WebLoginRequest request,
                                                  HttpServletRequest servletRequest) {
        String provider = StringUtils.hasText(request.getProvider())
                ? request.getProvider().trim()
                : SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD;
        IamUserEntity user;
        try {
            captchaVerificationSupport.enforce(provider, request.getUsername(),
                    request.getCaptchaId(), request.getCaptchaAnswer());
            user = SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD.equals(provider)
                    ? authenticationService.authenticate(request.getUsername(), request.getPassword())
                    : externalLoginService.credentialLogin(provider, request.getUsername(), request.getPassword());
        } catch (SimpleIamServerException exception) {
            auditEventPublisher.publishAuthentication(AuthenticationEventType.LOGIN_FAILED,
                    provider, request.getUsername(), null,
                    servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"),
                    exception.getErrorCode(), null);
            throw exception;
        }
        UserDetails userDetails = establishSession(user, servletRequest);
        auditEventPublisher.publishAuthentication(AuthenticationEventType.LOGIN_SUCCEEDED,
                provider, user.getUsername(), user.getId(),
                servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"),
                null, null);
        return ResponseEntity.ok(new WebLoginResponse("登录成功", toResponse(userDetails), false,
                Boolean.TRUE.equals(user.getMustChangePassword())));
    }

    /**
     * 发起跳转型登录（企业 SSO）：生成防重放 state 绑定当前浏览器会话，
     * 并暂存登录完成后的回跳目标（redirect 参数），返回外部 IdP 跳转地址。
     */
    @GetMapping("/authorize/{providerCode}")
    public ResponseEntity<WebAuthorizeResponse> authorize(@PathVariable String providerCode,
                                                          @RequestParam(required = false) String redirect,
                                                          HttpServletRequest request) {
        ExternalBrowserLoginProvider provider = providerRegistry.getBrowserLoginProvider(providerCode);
        if (provider == null) {
            throw new SimpleIamServerException(ErrorCode.EXTERNAL_PROVIDER_NOT_FOUND,
                    String.format(ServerErrorMessage.EXTERNAL_PROVIDER_NOT_FOUND, providerCode));
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        HttpSession session = request.getSession(true);
        session.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_EXTERNAL_LOGIN_STATE_PREFIX
                + providerCode, state);
        String safeRedirect = safeRedirectTarget(redirect);
        if (safeRedirect != null) {
            session.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_EXTERNAL_LOGIN_REDIRECT_PREFIX
                    + providerCode, safeRedirect);
        }
        String callbackUrl = buildCallbackUrl(request, providerCode);
        return ResponseEntity.ok(new WebAuthorizeResponse(provider.buildAuthorizeUrl(callbackUrl, state)));
    }

    /**
     * 跳转型登录回调（外部 IdP 重定向落点）：state 校验通过后换取并归一外部身份、
     * 建立 IAM 会话，成功 302 至登录完成目标（authorize 暂存的 redirect，缺省门户首页）；
     * 失败 302 回登录页并附 error 查询参数（错误码）。
     */
    @GetMapping("/callback/{providerCode}")
    public void callback(@PathVariable String providerCode,
                         @RequestParam Map<String, String> params,
                         HttpServletRequest request,
                         HttpServletResponse response) throws java.io.IOException {
        try {
            ExternalBrowserLoginProvider provider = providerRegistry.getBrowserLoginProvider(providerCode);
            if (provider == null) {
                throw new SimpleIamServerException(ErrorCode.EXTERNAL_PROVIDER_NOT_FOUND,
                        String.format(ServerErrorMessage.EXTERNAL_PROVIDER_NOT_FOUND, providerCode));
            }
            consumeState(request, providerCode, params.get("state"));
            IamUserEntity user = externalLoginService.completeBrowserLogin(provider, params);
            establishSession(user, request);
            auditEventPublisher.publishAuthentication(AuthenticationEventType.LOGIN_SUCCEEDED,
                    providerCode, user.getUsername(), user.getId(),
                    request.getRemoteAddr(), request.getHeader("User-Agent"), null, null);
        } catch (SimpleIamServerException exception) {
            String errorCode = exception.getErrorCode() == null ? "login-failed" : exception.getErrorCode();
            auditEventPublisher.publishAuthentication(AuthenticationEventType.LOGIN_FAILED,
                    providerCode, null, null,
                    request.getRemoteAddr(), request.getHeader("User-Agent"), errorCode, null);
            log.warn("跳转型登录失败重定向回登录页：provider={}, errorCode={}",
                    providerCode, errorCode, exception);
            String loginPage = request.getContextPath() + SimpleIamServerConstant.PATH_OAUTH2_LOGIN
                    + "?error=" + URLEncoder.encode(loginPageErrorCode(errorCode), StandardCharsets.UTF_8.name());
            response.sendRedirect(loginPage);
            return;
        }
        response.sendRedirect(request.getContextPath() + resolveCallbackTarget(request, providerCode));
    }

    /**
     * 登录页重定向 error 参数：异常码默认透传前端引导（外部身份源码为开放集合），
     * 仅账号锁定降级为 login-failed——锁定事实不向未认证方泄露，防回调爆破探测。
     */
    private String loginPageErrorCode(String errorCode) {
        return io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.ACCOUNT_LOCKED
                .equals(errorCode) ? "login-failed" : errorCode;
    }

    /**
     * 登出（吊销当前 IAM 会话）
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            Object iamSessionId = session.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_IAM_SESSION_ID);
            if (iamSessionId instanceof String) {
                try {
                    sessionService.revokeSession((String) iamSessionId);
                } catch (SimpleIamServerException ignored) {
                    // 会话已被其他退出路径撤销时保持幂等
                }
            }
            // 单端登出只踢本端 SSE 连接，同用户其他端不受影响（须在 invalidate 前取会话哈希）
            messageSseService.evictByServletSessionIdHash(TokenHashHelper.sha256Hex(session.getId()));
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (userDetails != null) {
            auditEventPublisher.publishAuthentication(AuthenticationEventType.LOGOUT,
                    null, userDetails.getUsername(),
                    userDetails instanceof IamUserDetailsSupport
                            ? ((IamUserDetailsSupport) userDetails).getUserId() : null,
                    servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"),
                    null, null);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 自助修改密码（首登强制改密与门户主动改密共用入口）。
     *
     * <p>成功后清会话内须改密快照（DB 标记由服务层清零，会话快照双保险），
     * 其余端会话与全部 OAuth2 授权被服务层吊销——旧密码签发的 token 一律失效。</p>
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request,
                                               HttpServletRequest servletRequest,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        if (!(userDetails instanceof IamUserDetailsSupport)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = ((IamUserDetailsSupport) userDetails).getUserId();
        HttpSession session = servletRequest.getSession(false);
        String currentSessionId = session == null ? null
                : (String) session.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_IAM_SESSION_ID);
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword(), currentSessionId);
        if (session != null) {
            session.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD, Boolean.FALSE);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<WebAuthUserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toResponse(userDetails));
    }

    /**
     * 登录端点异常分流：外部身份源不可用返回 503（服务故障不得伪装为凭据错误，
     * 且不计失败），登录方式不存在返回 404（契约约定），人机验证组件缺失返回 503，
     * 需要人机验证 / 验证码未通过返回 401 并置 captchaRequired=true（前端据此取题重试），
     * 其余业务失败统一 401。
     */
    @ExceptionHandler(SimpleIamServerException.class)
    public ResponseEntity<WebLoginResponse> handleLoginFailure(SimpleIamServerException e) {
        if (ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE.equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new WebLoginResponse(ServerErrorMessage.EXTERNAL_PROVIDER_UNAVAILABLE, null, false, false));
        }
        if (ErrorCode.EXTERNAL_PROVIDER_NOT_FOUND.equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new WebLoginResponse(e.getMessage(), null, false, false));
        }
        if (io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.CAPTCHA_PROVIDER_MISSING
                .equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new WebLoginResponse(ServerErrorMessage.CAPTCHA_PROVIDER_MISSING, null, false, false));
        }
        if (io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.CAPTCHA_REQUIRED
                .equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebLoginResponse(ServerErrorMessage.CAPTCHA_REQUIRED, null, true, false));
        }
        if (io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.CAPTCHA_INVALID
                .equals(e.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebLoginResponse(ServerErrorMessage.CAPTCHA_INVALID, null, true, false));
        }
        // 改密端点专属：请求内容问题回 400（旧密码错仍走兜底 401 凭据语义）
        if (io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED
                .equals(e.getErrorCode())
                || io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.PASSWORD_POLICY_VIOLATION
                .equals(e.getErrorCode())) {
            return ResponseEntity.badRequest()
                    .body(new WebLoginResponse(e.getMessage(), null, false, false));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new WebLoginResponse(e.getMessage(), null, false, false));
    }

    /**
     * 校验并一次性消费 state（防重放：仅发起 authorize 的浏览器会话可完成登录）。
     */
    private void consumeState(HttpServletRequest request, String providerCode, String callbackState) {
        if (!StringUtils.hasText(callbackState)) {
            throw new SimpleIamServerException(ServerErrorMessage.EXTERNAL_LOGIN_STATE_INVALID);
        }
        HttpSession session = request.getSession(false);
        Object expected = session == null ? null
                : session.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_EXTERNAL_LOGIN_STATE_PREFIX
                + providerCode);
        if (session != null) {
            session.removeAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_EXTERNAL_LOGIN_STATE_PREFIX
                    + providerCode);
        }
        if (expected == null || !expected.equals(callbackState)) {
            throw new SimpleIamServerException(ServerErrorMessage.EXTERNAL_LOGIN_STATE_INVALID);
        }
    }

    /**
     * 登录成功后建立浏览器会话（本地与外部登录共用）。
     */
    private UserDetails establishSession(IamUserEntity user, HttpServletRequest servletRequest) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        HttpSession session = servletRequest.getSession(true);
        sessionService.revokeActiveByServletSessionId(session.getId());
        session.setMaxInactiveInterval(properties.getSession().getExpiresIn());
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        IamSessionEntity iamSession = sessionService.createBoundSession(user.getId(), user.getUsername(),
                null, session.getId(), servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
        session.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_IAM_SESSION_ID, iamSession.getId());
        session.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION,
                user.getPermissionVersion() == null ? 0L : user.getPermissionVersion());
        session.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD,
                Boolean.TRUE.equals(user.getMustChangePassword()));
        return userDetails;
    }

    /**
     * 校验回跳目标：仅接受站内路径（以 / 开头且非 // 开头），防开放重定向；空或非法返回 null。
     */
    private String safeRedirectTarget(String redirect) {
        if (redirect == null || !redirect.startsWith("/") || redirect.startsWith("//")) {
            return null;
        }
        return redirect;
    }

    /**
     * 回调成功后的跳转目标：优先 authorize 时暂存的 redirect（取出即清，一次性），
     * 缺省统一应用门户首页，与本地密码登录成功的默认落点一致。
     */
    private String resolveCallbackTarget(HttpServletRequest request, String providerCode) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object redirect = session.getAttribute(
                    SimpleIamServerConstant.SESSION_ATTRIBUTE_EXTERNAL_LOGIN_REDIRECT_PREFIX + providerCode);
            session.removeAttribute(
                    SimpleIamServerConstant.SESSION_ATTRIBUTE_EXTERNAL_LOGIN_REDIRECT_PREFIX + providerCode);
            if (redirect instanceof String && safeRedirectTarget((String) redirect) != null) {
                return (String) redirect;
            }
        }
        return PORTAL_DEFAULT_TARGET;
    }

    private String buildCallbackUrl(HttpServletRequest request, String providerCode) {
        String baseUrl = properties.getExternalIdentity().getCallbackBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int port = request.getServerPort();
            baseUrl = scheme + "://" + serverName
                    + ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)
                    ? "" : ":" + port);
        } else if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + request.getContextPath() + "/iam/web/auth/callback/" + providerCode;
    }

    private WebAuthUserResponse toResponse(UserDetails userDetails) {
        Long userId = userDetails instanceof IamUserDetailsSupport
                ? ((IamUserDetailsSupport) userDetails).getUserId()
                : null;
        IamUserEntity user = userId == null ? null : userService.getById(userId);
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        return new WebAuthUserResponse(
                userId,
                userDetails.getUsername(),
                user == null ? userDetails.getUsername() : user.getDisplayName(),
                authorities.contains(SimpleIamServerConstant.ROLE_IAM_ADMIN),
                authorities);
    }
}
