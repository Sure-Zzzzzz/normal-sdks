package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.List;

/**
 * 资源服务安全策略配置器。
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

        http.requestMatcher(new OrRequestMatcher(createChainMatchers(protectedPaths, permitAllPaths)));
        http.authorizeRequests(authorize -> {
            if (!permitAllPaths.isEmpty()) {
                authorize.antMatchers(permitAllPaths.toArray(new String[0])).permitAll();
            }
            authorize.antMatchers(protectedPaths.toArray(new String[0])).authenticated();
            authorize.anyRequest().denyAll();
        });
        http.csrf(csrf -> csrf.ignoringAntMatchers(protectedPaths.toArray(new String[0])));
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
     * @param protectedPaths 受保护路径
     * @param permitAllPaths 公开路径
     * @return 路径匹配器数组
     */
    private static RequestMatcher[] createChainMatchers(List<String> protectedPaths, List<String> permitAllPaths) {
        String[] chainPaths = new String[protectedPaths.size() + permitAllPaths.size()];
        int index = 0;
        for (String protectedPath : protectedPaths) {
            chainPaths[index++] = protectedPath;
        }
        for (String permitAllPath : permitAllPaths) {
            chainPaths[index++] = permitAllPath;
        }
        return Arrays.stream(chainPaths).map(AntPathRequestMatcher::new).toArray(RequestMatcher[]::new);
    }
}
