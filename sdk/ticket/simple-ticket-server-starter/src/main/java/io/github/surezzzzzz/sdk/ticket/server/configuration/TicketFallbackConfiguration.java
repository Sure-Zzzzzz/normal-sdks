package io.github.surezzzzzz.sdk.ticket.server.configuration;

import io.github.surezzzzzz.sdk.ticket.server.spi.NotificationProvider;
import io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SPI 降级适配装配（散形态默认；iam-adaptor 提供真实现时整体让位——DESIGN §5.2 聚散）。
 *
 * <p>Noop 必须经配置类 @ConditionalOnMissingBean 注册而非组件扫描——
 * 扫描件上的条件注解对同类 Bean 缺席判断不可靠，adaptor 接入时会双 Bean 冲突。</p>
 *
 * @author surezzzzzz
 */
@Configuration
public class TicketFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TicketFallbackConfiguration.class);

    /**
     * 通知降级（日志承载，流转不受影响）。
     *
     * @return NotificationProvider
     */
    @Bean
    @ConditionalOnMissingBean(NotificationProvider.class)
    public NotificationProvider noopNotificationProvider() {
        log.info("工单通知出口：Noop 日志降级（散形态）");
        return new NotificationProvider() {
            @Override
            public void notify(List<String> subjectIds, String title, String content) {
                if (log.isDebugEnabled()) {
                    log.debug("工单通知（Noop 降级）：recipients={}", subjectIds.size());
                }
            }
        };
    }

    /**
     * 人员档案降级（显示 subjectId）。
     *
     * @return SubmitterProfileProvider
     */
    @Bean
    @ConditionalOnMissingBean(SubmitterProfileProvider.class)
    public SubmitterProfileProvider noopSubmitterProfileProvider() {
        log.info("工单人员档案出口：Noop 显示 subjectId（散形态）");
        return new SubmitterProfileProvider() {
            @Override
            public String displayNameOf(String subjectId) {
                return subjectId;
            }
        };
    }
}
