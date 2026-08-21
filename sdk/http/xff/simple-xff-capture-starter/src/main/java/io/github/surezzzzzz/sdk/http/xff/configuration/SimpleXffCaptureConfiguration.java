package io.github.surezzzzzz.sdk.http.xff.configuration;

import io.github.surezzzzzz.sdk.http.xff.SimpleXffCapturePackage;
import io.github.surezzzzzz.sdk.http.xff.annotation.SimpleXffCaptureComponent;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureWebConstant;
import io.github.surezzzzzz.sdk.http.xff.filter.SimpleXffCaptureFilter;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataCapturePreparer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

/**
 * Simple XFF Capture 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpServletRequest.class, OncePerRequestFilter.class})
@EnableConfigurationProperties(SimpleXffCaptureProperties.class)
@ComponentScan(
        basePackageClasses = SimpleXffCapturePackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleXffCaptureComponent.class)
)
@ConditionalOnProperty(prefix = SimpleXffCaptureConstant.CONFIG_PREFIX,
        name = SimpleXffCaptureConstant.CONFIG_ENABLE,
        havingValue = SimpleXffCaptureConstant.CONFIG_VALUE_TRUE)
public class SimpleXffCaptureConfiguration {

    /**
     * 注册 XFF 自动采集 Filter。
     *
     * @param xffCaptureService XFF 采集服务
     * @param properties        XFF Capture 配置
     * @return Filter 注册 Bean
     */
    @Bean(name = SimpleXffCaptureWebConstant.FILTER_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleXffCaptureWebConstant.FILTER_BEAN_NAME)
    public FilterRegistrationBean<SimpleXffCaptureFilter> simpleXffCaptureFilterRegistration(
            XffCaptureService xffCaptureService, SimpleXffCaptureProperties properties) {
        FilterRegistrationBean<SimpleXffCaptureFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SimpleXffCaptureFilter(xffCaptureService,
                properties.getExcludedPathPatterns(), new RequestDataCapturePreparer(properties)));
        registrationBean.setName(SimpleXffCaptureWebConstant.FILTER_NAME);
        registrationBean.setUrlPatterns(Collections.singletonList(SimpleXffCaptureWebConstant.FILTER_URL_PATTERN));
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST);
        registrationBean.setOrder(properties.getOrder());
        return registrationBean;
    }
}
