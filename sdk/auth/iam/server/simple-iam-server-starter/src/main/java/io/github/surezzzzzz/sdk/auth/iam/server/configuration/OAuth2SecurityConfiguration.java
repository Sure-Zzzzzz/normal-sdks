package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.filter.IamMustChangePasswordFilter;
import io.github.surezzzzzz.sdk.auth.iam.server.filter.IamResourceVerificationClientAuthenticationFilter;
import io.github.surezzzzzz.sdk.auth.iam.server.filter.IamSessionValidationFilter;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 / IAM Security Configuration
 *
 * <p>七条 SecurityFilterChain：
 * <ol>
 *   <li>Order(0) Error Dispatch {@code /error}：permitAll，保住 sendError 的原始状态码不被兜底链覆盖。</li>
 *   <li>Order(1) Authorization Server：SAS 标准端点，CSRF 关闭。</li>
 *   <li>Order(2) Resource verification {@code /iam/resource/**}：独立客户端 Basic 认证。</li>
 *   <li>Order(3) Web API {@code /iam/web/**}：登录态和普通用户 JSON API。</li>
 *   <li>Order(4) Admin API {@code /iam/admin/**}：需 ROLE_iam_admin 或任一页面权限码（入口门），方法级 @PreAuthorize 逐端点强制 + CSRF 开启。</li>
 *   <li>Order(5) IAM Application {@code /iam/**}：认证 + CSRF 开启。</li>
 *   <li>Order(6) Fallback {@code /**}：denyAll。</li>
 * </ol>
 *
 * <p>开放 API {@code /iam/api/**}（AKSK 凭证主体）不在本类七链内：宿主配置
 * protected-paths 后由公共资源层链（simple-resource-server-starter，HIGHEST_PRECEDENCE）
 * 接管——STATELESS、排斥 Cookie、无 CSRF、失败统一 401/403；未配置时该路径落
 * Order(5) 会话链拒绝（失败关闭）。见 {@link SimpleIamServerStartupValidator}。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class OAuth2SecurityConfiguration {

    private final SimpleIamServerProperties properties;
    private final IamUserRepository userRepository;
    private final IamSessionValidationFilter iamSessionValidationFilter;
    /**
     * 须改密拦截器条件装配（password.must-change-enforcement=false 时缺位），
     * 延迟解析避免配置类构造对可缺位组件的硬依赖
     */
    private final ObjectProvider<IamMustChangePasswordFilter> iamMustChangePasswordFilterProvider;

    /**
     * Order(0) Error Dispatch {@code /error}：permitAll。
     *
     * <p>安全链上任何 {@code sendError}（如公共资源层对无凭据/坏凭据的 401）会触发容器
     * ERROR dispatch 转发 {@code /error}，而 DelegatingFilterProxy 默认同样过滤 ERROR
     * dispatch——若 {@code /error} 落兜底 denyAll 链，原始状态码会被覆盖成 403 空 body。
     * 本链放行 {@code /error} 交给 BasicErrorController，保住原始状态码与错误 body。</p>
     */
    @Bean
    @Order(0)
    public SecurityFilterChain errorDispatchSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher(SimpleIamServerConstant.PATH_ERROR)
                .authorizeRequests(authorize -> authorize
                        .anyRequest().permitAll())
                .formLogin().disable()
                .httpBasic().disable()
                .csrf().disable();
        return http.build();
    }

    /**
     * Order(1) Authorization Server
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = http.getConfigurer(
                OAuth2AuthorizationServerConfigurer.class);
        authorizationServerConfigurer
                .oidc(oidc -> oidc.userInfoEndpoint(userInfo -> userInfo
                        .userInfoMapper(context -> toUserInfo(context.getAuthorization().getPrincipalName(),
                                context.getAuthorization().getAuthorizedScopes()))))
                .authorizationEndpoint(endpoint -> endpoint
                        .consentPage(SimpleIamServerConstant.PATH_OAUTH2_CONSENT)
                        .authenticationProviders(providers -> providers.stream()
                                .filter(OAuth2AuthorizationCodeRequestAuthenticationProvider.class::isInstance)
                                .map(OAuth2AuthorizationCodeRequestAuthenticationProvider.class::cast)
                                .forEach(provider -> provider.setAuthenticationValidator(
                                        new OAuth2AuthorizationCodeRequestAuthenticationValidator()
                                                .andThen(this::validatePkceMethod)))));
        // 未认证的浏览器授权请求跳 Vue 登录页，带上原始 URL 作为 redirect 参数
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    String originalUri = request.getRequestURI();
                    String query = request.getQueryString();
                    String fullPath = query != null ? originalUri + "?" + query : originalUri;
                    response.sendRedirect(SimpleIamServerConstant.PATH_OAUTH2_LOGIN
                            + "?redirect=" + URLEncoder.encode(fullPath, StandardCharsets.UTF_8.name()));
                }));
        http.oauth2ResourceServer().jwt();
        http.addFilterAfter(iamSessionValidationFilter, SecurityContextPersistenceFilter.class);
        addMustChangePasswordFilter(http);
        log.info("IAM Authorization Server security filter chain initialized with OIDC, client basic authentication and JWT bearer authentication");
        return http.build();
    }

    /**
     * 须改密拦截器挂链（会话校验之后）：组件装配时挂入四条会话链，条件关闭时跳过。
     */
    private void addMustChangePasswordFilter(HttpSecurity http) throws Exception {
        IamMustChangePasswordFilter filter = iamMustChangePasswordFilterProvider.getIfAvailable();
        if (filter != null) {
            http.addFilterAfter(filter, IamSessionValidationFilter.class);
        }
    }

    /**
     * 公共客户端仅允许 S256 PKCE。默认的 redirect_uri 与 scope 校验由 SAS 标准校验器先执行。
     */
    private void validatePkceMethod(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        RegisteredClient registeredClient = context.getRegisteredClient();
        if (registeredClient != null && registeredClient.getClientSettings().isRequireProofKey()) {
            OAuth2AuthorizationCodeRequestAuthenticationToken authRequest = context.getAuthentication();
            Object method = authRequest.getAdditionalParameters() != null
                    ? authRequest.getAdditionalParameters().get("code_challenge_method")
                    : null;
            if (!"S256".equals(method)) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                        "公共客户端必须使用 code_challenge_method=S256", null);
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, authRequest);
            }
        }
    }

    private OidcUserInfo toUserInfo(String username, java.util.Set<String> scopes) {
        IamUserEntity user = userRepository.findByUsername(username).orElseThrow(
                () -> new SimpleIamServerException(ErrorCode.USER_NOT_FOUND, "OIDC主体不存在：" + username));
        OidcUserInfo.Builder builder = OidcUserInfo.builder().subject(String.valueOf(user.getId()));
        if (scopes.contains("profile")) {
            builder.name(user.getDisplayName()).preferredUsername(user.getUsername());
        }
        if (scopes.contains("email") && user.getEmail() != null) {
            builder.email(user.getEmail());
        }
        if (scopes.contains("phone") && user.getPhone() != null) {
            builder.phoneNumber(user.getPhone());
        }
        return builder.build();
    }

    /**
     * Order(2) Resource verification：独立客户端 Basic 认证。
     *
     * <p>无状态机器端点：SecurityContext 仅存活于单请求（principal 为验证客户端实体，
     * 不入会话）。必须显式挂 {@link NullSecurityContextRepository}，否则
     * SecurityContextPersistenceFilter 链尾会把 SecurityContext 隐式写入会话，
     * spring-session 下 JDK 序列化 JPA 实体即 500。</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain resourceVerificationSecurityFilterChain(HttpSecurity http,
                                                                       IamResourceVerificationClientAuthenticationFilter resourceVerificationClientAuthenticationFilter) throws Exception {
        http
                .antMatcher(SimpleIamServerConstant.PATH_RESOURCE_API)
                .authorizeRequests(authorize -> authorize.anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .csrf().disable()
                .formLogin().disable()
                .httpBasic().disable()
                .addFilterAfter(resourceVerificationClientAuthenticationFilter,
                        SecurityContextPersistenceFilter.class);
        return http.build();
    }

    /**
     * Order(3) Web API：/iam/web/**
     */
    @Bean
    @Order(3)
    public SecurityFilterChain webAuthApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher(SimpleIamServerConstant.PATH_WEB_API)
                .authorizeRequests(authorize -> authorize
                        .antMatchers(SimpleIamServerConstant.PATH_WEB_AUTH_CSRF,
                                SimpleIamServerConstant.PATH_WEB_AUTH_LOGIN,
                                SimpleIamServerConstant.PATH_WEB_AUTH_PROVIDERS,
                                SimpleIamServerConstant.PATH_WEB_AUTH_CAPTCHA,
                                SimpleIamServerConstant.PATH_WEB_AUTH_AUTHORIZE,
                                SimpleIamServerConstant.PATH_WEB_AUTH_CALLBACK,
                                SimpleIamServerConstant.PATH_WEB_BRANDING)
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository()))
                .httpBasic().disable()
                .addFilterAfter(iamSessionValidationFilter, SecurityContextPersistenceFilter.class);
        addMustChangePasswordFilter(http);
        log.info("IAM Web API security filter chain initialized");
        return http.build();
    }

    /**
     * Order(4) Admin API：/iam/admin/**
     *
     * <p>入口门放宽为 {@link SimpleIamServerConstant#ADMIN_CONSOLE_ENTRANCE_AUTHORITIES}
     * （iam_admin 角色或任一页面权限码），使持部分权限码的委派用户可进入管理台；
     * 端点级鉴权不因门放宽而削弱——全部 admin 端点仍由 @PreAuthorize 逐个强制，
     * IamAdminApiPermissionEnforcementTest 保证无端点漏标。未认证请求返 401
     * （与 Order(3) 对齐，前端 401 分支统一跳登录），已认证但无权限仍默认 403。
     */
    @Bean
    @Order(4)
    public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher(SimpleIamServerConstant.PATH_ADMIN_API)
                .authorizeRequests(authorize -> authorize
                        .anyRequest().hasAnyAuthority(SimpleIamServerConstant.ADMIN_CONSOLE_ENTRANCE_AUTHORITIES))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
                .httpBasic().disable()
                .addFilterAfter(iamSessionValidationFilter, SecurityContextPersistenceFilter.class);
        addMustChangePasswordFilter(http);
        log.info("IAM Admin API security filter chain initialized (entrance=iam_admin role or any page permission, mfa.step-up no-op when disabled)");
        return http.build();
    }

    /**
     * Order(5) IAM Application：/iam/**
     */
    @Bean
    @Order(5)
    public SecurityFilterChain iamAppSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher(SimpleIamServerConstant.PATH_IAM)
                .authorizeRequests(authorize -> authorize
                        .anyRequest().authenticated())
                .httpBasic().disable()
                .addFilterAfter(iamSessionValidationFilter, SecurityContextPersistenceFilter.class);
        addMustChangePasswordFilter(http);
        log.info("IAM Application security filter chain initialized");
        return http.build();
    }

    /**
     * Order(6) Fallback：denyAll
     */
    @Bean
    @Order(6)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .antMatcher(SimpleIamServerConstant.PATH_FALLBACK)
                .authorizeRequests(authorize -> authorize
                        .anyRequest().denyAll())
                .formLogin().disable()
                .httpBasic().disable()
                .csrf().disable();
        return http.build();
    }

    /**
     * 验证客户端认证 Filter 以组件扫描注册为普通 Bean 后，Spring Boot 会自动给它挂
     * FilterRegistrationBean（默认纳入全局过滤链）；本注册显式 {@code setEnabled(false)}
     * 关掉自动注册，使其只在 Order 2 链内按 {@code addFilterBefore} 精确挂载。
     */
    @Bean
    public FilterRegistrationBean<IamResourceVerificationClientAuthenticationFilter> resourceVerificationClientFilterRegistration(
            IamResourceVerificationClientAuthenticationFilter resourceVerificationClientAuthenticationFilter) {
        FilterRegistrationBean<IamResourceVerificationClientAuthenticationFilter> registration =
                new FilterRegistrationBean<IamResourceVerificationClientAuthenticationFilter>(
                        resourceVerificationClientAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 把 Servlet 容器级会话超时对齐 {@code session.expires-in}（换算为分钟、下限 1），
     * 兜住未登录匿名会话（如仅取过 CSRF token 的会话）的容器侧过期；
     * 已登录会话的超时在建会话时逐会话覆写。
     */
    @Bean
    public ServletContextInitializer iamSessionTimeoutInitializer() {
        return servletContext -> servletContext.setSessionTimeout(Math.max(1,
                properties.getSession().getExpiresIn() / SimpleIamServerConstant.SECONDS_PER_MINUTE));
    }

    /**
     * CSRF token 存 HttpSession：匿名可取、登录后沿用，配合 spring-session Redis
     * 实现多实例下 CSRF 会话互通；以独立 Bean 暴露供 Order 1 链与 Web 层共用。
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    /**
     * 委托编码器（BCrypt 优先、兼容历史格式）：既能校验旧格式存量哈希，
     * 又允许宿主以同名 Bean 替换为自有编码策略。
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
