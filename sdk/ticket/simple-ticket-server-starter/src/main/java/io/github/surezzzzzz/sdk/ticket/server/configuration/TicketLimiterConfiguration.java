package io.github.surezzzzzz.sdk.ticket.server.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;

/**
 * 工单限流 KeyProvider 装配（老大 2026-10-11 定案：单 smart 双窗口——每秒突发+每日配额）。
 *
 * <p>两个 KeyProvider 供宿主拦截器规则引用（规则在宿主 yml，limits 双窗：每秒 2 次+每天 20 次）；
 * 主体维度从认证桥派生，不信任请求业务字段。limiter 不在场（散形态）本配置整体让位。</p>
 *
 * <p>宿主规则示例（application.yml，路径按 apiBasePath 实际值）：</p>
 * <pre>
 * io.github.surezzzzzz.sdk.limiter.redis.smart:
 *   enable: true
 *   interceptor.rules:
 *     - pathPattern: /api/ticket/portal/tickets
 *       method: POST
 *       keyProvider: ticketSubjectKeyProvider
 *       limits: [{count: 2, window: 1, unit: SECONDS}, {count: 20, window: 1, unit: DAYS}]
 *     - pathPattern: /api/ticket/openapi/tickets
 *       method: POST
 *       keyProvider: ticketCredentialKeyProvider
 *       limits: [{count: 2, window: 1, unit: SECONDS}, {count: 50, window: 1, unit: DAYS}]
 * </pre>
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnClass(SmartRedisLimiterKeyProvider.class)
public class TicketLimiterConfiguration {

    /**
     * 人员维度 Key（portal 提交端点规则引用）。
     *
     * @param principalResolver 认证桥
     * @return KeyProvider
     */
    @Bean
    @ConditionalOnMissingBean(name = "ticketSubjectKeyProvider")
    public SmartRedisLimiterKeyProvider ticketSubjectKeyProvider(TicketPrincipalResolver principalResolver) {
        return new SmartRedisLimiterKeyProvider() {
            @Override
            public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
                TicketRequestContext ticketContext = principalResolver.resolve(request);
                if (ticketContext == null || ticketContext.isMachine()) {
                    return "anonymous";
                }
                return "subject:" + ticketContext.getSubjectId();
            }
        };
    }

    /**
     * 凭据维度 Key（openapi 提交端点规则引用）。
     *
     * @param principalResolver 认证桥
     * @return KeyProvider
     */
    @Bean
    @ConditionalOnMissingBean(name = "ticketCredentialKeyProvider")
    public SmartRedisLimiterKeyProvider ticketCredentialKeyProvider(TicketPrincipalResolver principalResolver) {
        return new SmartRedisLimiterKeyProvider() {
            @Override
            public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
                TicketRequestContext ticketContext = principalResolver.resolve(request);
                if (ticketContext == null || !ticketContext.isMachine()) {
                    return "anonymous";
                }
                return "credential:" + ticketContext.getSubjectId();
            }
        };
    }

}
