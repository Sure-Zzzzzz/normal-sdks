package io.github.surezzzzzz.sdk.ticket.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketResourceServerPrincipalResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证桥装配（resource-core 在场时注册默认桥；双缺席时启动即失败——无匿名回退，DESIGN §5.3）。
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnClass(VerifiedResourceContext.class)
public class TicketBridgeConfiguration {

    /**
     * 默认桥（宿主可整体替换）。
     *
     * @return 桥实现
     */
    @Bean
    @ConditionalOnMissingBean(TicketPrincipalResolver.class)
    public TicketPrincipalResolver ticketPrincipalResolver() {
        return new TicketResourceServerPrincipalResolver();
    }
}
