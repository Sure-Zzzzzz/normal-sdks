package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.SmartRedisLimiterInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Sure.
 * @description 拦截器注册配置
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@Slf4j
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterWebMvcConfigurer implements WebMvcConfigurer {

    @Autowired(required = false)
    private SmartRedisLimiterInterceptor limiterInterceptor;

    @Autowired
    private SmartRedisLimiterProperties properties;

    @PostConstruct
    public void init() {
        if (limiterInterceptor != null) {
            log.info("SmartRedisLimiterWebMvcConfigurer 初始化完成");
        } else {
            log.warn("SmartRedisLimiterInterceptor 未找到，拦截器将不会注册");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (limiterInterceptor != null) {
            List<String> excludePatterns = getExcludePatterns();

            registry.addInterceptor(limiterInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns(excludePatterns.toArray(new String[0]));

            log.info("SmartRedisLimiter 拦截器注册成功, 排除路径: {}", excludePatterns);
        }
    }

    private List<String> getExcludePatterns() {
        List<String> patterns = new ArrayList<>();

        patterns.add("/actuator/**");
        patterns.add("/actuator");
        patterns.add("/health");
        patterns.add("/health/**");

        List<String> userExcludes = properties.getInterceptor().getExcludePatterns();
        if (userExcludes != null && !userExcludes.isEmpty()) {
            patterns.addAll(userExcludes);
        }

        return patterns;
    }
}
