package io.github.surezzzzzz.sdk.ticket.server.configuration;

import io.github.surezzzzzz.sdk.ticket.server.SimpleTicketServerPackage;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 工单服务自动配置（javax 线 spring.factories 注册）。
 *
 * <p>启用条件：总开关 {@code io.github.surezzzzzz.sdk.ticket.server.enable=true}（默认开，
 * 缺数据源/认证桥时启动即响亮失败，不做静默半开）。组件扫描只认本模块标记注解；
 * 协作装配件（Resource 双 Provider/limiter）由宿主自引，缺位时对应能力让位（DESIGN §5.3）。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnProperty(prefix = SimpleTicketServerConstant.CONFIG_PREFIX, name = "enable",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpleTicketServerProperties.class)
@EnableScheduling
@Import({TicketBridgeConfiguration.class, TicketFallbackConfiguration.class,
        TicketFileConfiguration.class, TicketLimiterConfiguration.class})
@ComponentScan(
        basePackageClasses = SimpleTicketServerPackage.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SimpleTicketServerComponent.class),
        useDefaultFilters = false
)
public class SimpleTicketServerAutoConfiguration {
}
