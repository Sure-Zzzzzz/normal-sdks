package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.List;

/**
 * 资源服务安全策略配置器（Security 6.4 / jakarta 线）。
 *
 * <p>与 javax 线的差异仅两处：授权 DSL 由 authorizeRequests（Security 6 移除）换为
 * authorizeHttpRequests，CSRF 忽略改传匹配器对象。链级匹配器保持 AntPathRequestMatcher——
 * 实证（spring-security-web 6.4.2 jar）：PathPatternRequestMatcher 为 Security 6.5 才引入的
 * API，SB 3.4.2 基线不存在该类；保持 Ant 系使链级匹配语义与 javax 线逐字一致，
 * 待基线升级到 6.5+ 时再单独评估切换。SDK 自身路径语义仍由 ResourceSecurityPathHelper
 * 的 spring-core AntPathMatcher 决定。
 *
 * @author surezzzzzz
 */
final class ResourceServerSecurityConfigurer {

    private ResourceServerSecurityConfigurer() {
        throw new UnsupportedOperationException("资源服务安全策略配置器不能实例化");
    }

    /**
     * 配置资源服务安全策略。
     *
     * @param http           Spring Security配置器
     * @param properties     资源服务配置
     * @param engine         资源认证编排引擎
     * @param eventPublisher 已验证访问事件发布器
     * @param environment    Spring环境
     * @throws Exception 配置异常
     */
    static void configure(HttpSecurity http, ResourceServerProperties properties, ResourceServerEngine engine,
                          ApplicationEventPublisher eventPublisher, Environment environment) throws Exception {
        String contextPath = environment.getProperty(
                SimpleResourceServerStarterConstant.PROPERTY_SERVER_SERVLET_CONTEXT_PATH);
        List<String> protectedPaths = ResourceSecurityPathHelper.normalizePaths(
                properties.getSecurity().getProtectedPaths(), contextPath,
                properties.getSecurity().isContextPathAware());
        List<String> permitAllPaths = ResourceSecurityPathHelper.normalizePaths(
                properties.getSecurity().getPermitAllPaths(), contextPath,
                properties.getSecurity().isContextPathAware());
        ResourceSecurityPathHelper.validateNoOverlap(permitAllPaths, protectedPaths);

        RequestMatcher[] protectedMatchers = createMatchers(protectedPaths);
        RequestMatcher[] permitAllMatchers = createMatchers(permitAllPaths);
        RequestMatcher[] chainMatchers = new RequestMatcher[protectedMatchers.length + permitAllMatchers.length];
        System.arraycopy(protectedMatchers, 0, chainMatchers, 0, protectedMatchers.length);
        System.arraycopy(permitAllMatchers, 0, chainMatchers, protectedMatchers.length, permitAllMatchers.length);
        http.securityMatcher(new OrRequestMatcher(chainMatchers));
        // 机器接口链：Bearer 单一凭据语义排斥 Cookie，session 永远不可能被消费；
        // 若不设 STATELESS，Spring Security 默认会把首次认证存入 HttpSession 并下发
        // Set-Cookie，客户端回发即触发 resolver 的 CREDENTIAL_AMBIGUOUS 拒绝
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(authorize -> {
            if (permitAllMatchers.length > 0) {
                authorize.requestMatchers(permitAllMatchers).permitAll();
            }
            authorize.requestMatchers(protectedMatchers).authenticated();
            authorize.anyRequest().denyAll();
        });
        http.csrf(csrf -> csrf.ignoringRequestMatchers(chainMatchers));
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, exception) -> response.sendError(
                        SimpleResourceServerStarterConstant.HTTP_STATUS_UNAUTHORIZED,
                        SimpleResourceServerStarterConstant.MESSAGE_UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) -> response.sendError(
                        SimpleResourceServerStarterConstant.HTTP_STATUS_FORBIDDEN,
                        SimpleResourceServerStarterConstant.MESSAGE_FORBIDDEN)));
        http.addFilterBefore(new ResourceAuthenticationFilter(engine, protectedPaths, eventPublisher),
                AnonymousAuthenticationFilter.class);
    }

    /**
     * 创建资源安全链路径匹配器。
     *
     * @param paths 路径模式
     * @return 路径匹配器数组
     */
    private static RequestMatcher[] createMatchers(List<String> paths) {
        // Security 6.4.2 无 PathPatternRequestMatcher（6.5 才引入，见类注释）；Ant 系语义与 javax 线一致
        return paths.stream()
                .map(AntPathRequestMatcher::new)
                .toArray(RequestMatcher[]::new);
    }
}
