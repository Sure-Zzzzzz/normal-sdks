package io.github.surezzzzzz.sdk.audit.http.xff.configuration;

import io.github.surezzzzzz.sdk.audit.http.xff.SimpleXffCaptureAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.http.xff.annotation.SimpleXffCaptureAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Simple XFF Capture Audit Listener 自动配置。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleXffCaptureAuditListenerProperties.class)
@ConditionalOnProperty(prefix = SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX,
        name = SimpleXffCaptureAuditListenerConstant.CONFIG_ENABLE,
        havingValue = SimpleXffCaptureAuditListenerConstant.CONFIG_VALUE_TRUE)
@ComponentScan(
        basePackageClasses = SimpleXffCaptureAuditListenerPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleXffCaptureAuditListenerComponent.class)
)
public class SimpleXffCaptureAuditListenerConfiguration {

    /**
     * 注册 Listener 专用有界执行器。
     *
     * @param properties Listener 配置
     * @return 有界执行器
     */
    @Bean(name = SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME)
    public Executor xffCaptureAuditExecutor(SimpleXffCaptureAuditListenerProperties properties) {
        SimpleXffCaptureAuditListenerProperties.Executor config = properties.getExecutor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setThreadNamePrefix(SimpleXffCaptureAuditListenerConstant.EXECUTOR_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(config.getAwaitTerminationSeconds());
        executor.initialize();
        log.info("初始化 XFF Capture 审计执行器：coreSize={}, maxSize={}, queueCapacity={}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity());
        return executor;
    }
}
