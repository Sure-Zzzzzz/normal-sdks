package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 对外 REST 固定 token 兜底安全配置
 *
 * <p>仅当 api 开启且 resource-server 显式关闭（io...resource.server.enabled=false）时装配。
 * resource-server 默认开启（matchIfMissing=true），开启时本配置类不装配，REST 链由 resource-server 接管；
 * 显式关闭时由 management 用固定 token 护住 api.base-path/v1/policy/**。
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
@Conditional(SmartRedisLimiterManagementRestSecurityConfiguration.RestTokenEnabledCondition.class)
public class SmartRedisLimiterManagementRestSecurityConfiguration {

    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 校验固定 token 非空
     */
    @PostConstruct
    public void validate() {
        if (!hasText(properties.getRest().getPolicyToken())) {
            throw new SmartRedisLimiterManagementConfigurationException(
                    ErrorCode.CONFIG_REST_TOKEN_REQUIRED,
                    ErrorMessage.CONFIG_REST_TOKEN_REQUIRED);
        }
    }

    /**
     * 对外 REST 窄路径固定 token 安全链，覆盖 api.base-path/v1/policy/** 下的快照和 CRUD
     */
    @Bean
    @Order(SmartRedisLimiterManagementConstant.DEFAULT_SECURITY_ORDER)
    public SecurityFilterChain managementRestSecurityFilterChain(HttpSecurity http) throws Exception {
        String restBasePath = properties.getApi().getBasePath()
                + "/v1/policy"
                + SmartRedisLimiterManagementConstant.PATH_WILDCARD_SUFFIX;
        http
                .antMatcher(restBasePath)
                .csrf()
                .disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(new PolicyTokenAuthenticationFilter(properties.getRest().getPolicyToken()),
                        AnonymousAuthenticationFilter.class)
                .authorizeRequests()
                .anyRequest()
                .authenticated()
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED));
        return http.build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 固定 token 认证过滤器
     */
    private static final class PolicyTokenAuthenticationFilter extends OncePerRequestFilter {

        private final byte[] expectedToken;

        private PolicyTokenAuthenticationFilter(String policyToken) {
            this.expectedToken = policyToken.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            List<String> tokens = Collections.list(
                    request.getHeaders(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN));
            if (tokens.size() != 1 || !hasText(tokens.get(0))
                    || !MessageDigest.isEqual(expectedToken, tokens.get(0).getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(UNAUTHORIZED.value());
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null
                    || SecurityContextHolder.getContext().getAuthentication()
                    instanceof AnonymousAuthenticationToken) {
                SecurityContextHolder.getContext().setAuthentication(new PreAuthenticatedAuthenticationToken(
                        SmartRedisLimiterManagementConstant.POLICY_TOKEN_PRINCIPAL,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(
                                SmartRedisLimiterManagementConstant.POLICY_TOKEN_AUTHORITY))));
            }
            filterChain.doFilter(request, response);
        }

        private boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    /**
     * 固定 token 兜底链装配条件：api 开启且 resource-server 显式关闭
     *
     * <p>resource.server.enabled 不配时 matchIfMissing=false 不满足，resource-server 默认开启接管；
     * 显式配 false 时固定 token 装配兜底。
     */
    static class RestTokenEnabledCondition extends AllNestedConditions {

        RestTokenEnabledCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(
                prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".api",
                name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
                havingValue = "true")
        static class ApiEnabled {
        }

        @ConditionalOnProperty(
                prefix = SmartRedisLimiterManagementConstant.RESOURCE_SERVER_CONFIG_PREFIX,
                name = "enabled",
                havingValue = "false",
                matchIfMissing = false)
        static class ResourceServerDisabled {
        }
    }
}
