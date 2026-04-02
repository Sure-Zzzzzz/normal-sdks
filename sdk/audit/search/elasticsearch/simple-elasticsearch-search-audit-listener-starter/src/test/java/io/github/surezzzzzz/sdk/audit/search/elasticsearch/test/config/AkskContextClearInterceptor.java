package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.config;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider.AkskContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * AkskContextHolder 清理拦截器
 *
 * <p>在每次请求结束后（无论成功或异常）清理 ThreadLocal，
 * 防止 Servlet 容器线程池复用导致内存泄漏或数据污染。
 *
 * <p>清理时机为 {@code afterCompletion}，确保即使 Controller 抛异常也能清理。
 *
 * @author surezzzzzz
 * @see AkskContextHolder
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(
        prefix = "test.es.audit",
        name = "provider-type",
        havingValue = "aksk-event"
)
@Slf4j
public class AkskContextClearInterceptor implements WebMvcConfigurer, HandlerInterceptor {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AkskContextHolder.clear();
        log.debug("AkskContextHolder cleared for request: {} {}", request.getMethod(), request.getRequestURI());
    }
}
