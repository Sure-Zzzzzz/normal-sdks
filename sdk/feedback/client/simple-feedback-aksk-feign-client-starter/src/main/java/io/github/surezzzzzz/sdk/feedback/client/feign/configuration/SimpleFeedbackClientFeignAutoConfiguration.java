package io.github.surezzzzzz.sdk.feedback.client.feign.configuration;

import io.github.surezzzzzz.sdk.feedback.client.FeedbackClient;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClient;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClientAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Feedback Feign wire 适配自动配置；Feign 代理由宿主 {@code @EnableFeignClients} 扫描。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({FeedbackClient.class, FeedbackFeignClient.class})
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.feedback.client",
        name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(FeedbackFeignClient.class)
public class SimpleFeedbackClientFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeedbackClient.class)
    public FeedbackClient feedbackClient(FeedbackFeignClient delegate) {
        return new FeedbackFeignClientAdapter(delegate);
    }
}
