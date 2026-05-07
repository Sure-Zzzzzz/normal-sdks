package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.configuration;

import feign.Feign;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.interceptor.AkskHttpSessionFeignRequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK Feign HttpSession Client Auto Configuration
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass(Feign.class)
@ConditionalOnBean(TokenManager.class)
public class AkskFeignHttpSessionClientAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== Simple AKSK Feign HttpSession Client 自动配置加载成功 =====");
    }

    @Bean
    public AkskHttpSessionFeignRequestInterceptor akskHttpSessionFeignRequestInterceptor(TokenManager tokenManager) {
        log.info("Creating AkskHttpSessionFeignRequestInterceptor Bean");
        return new AkskHttpSessionFeignRequestInterceptor(tokenManager);
    }
}
