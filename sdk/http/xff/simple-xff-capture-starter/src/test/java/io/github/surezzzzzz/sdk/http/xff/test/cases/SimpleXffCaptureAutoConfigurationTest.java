package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureConfiguration;
import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureWebConstant;
import io.github.surezzzzzz.sdk.http.xff.filter.SimpleXffCaptureFilter;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Simple XFF Capture 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class)
class SimpleXffCaptureAutoConfigurationTest {

    private static final int CONFIGURED_FILTER_ORDER = -123456789;
    private static final int DEFAULT_FILTER_ORDER = Integer.MAX_VALUE - 100;

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DispatcherServletAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    SimpleXffCaptureConfiguration.class));

    @Test
    void shouldNotRegisterWhenDisabled() {
        contextRunner.withPropertyValues(SimpleXffCaptureConstant.CONFIG_PREFIX + ".enable=false")
                .run(context -> {
                    log.info("关闭时 Service 数量：{}，Filter 数量：{}",
                            context.getBeansOfType(XffCaptureService.class).size(),
                            context.getBeansOfType(FilterRegistrationBean.class).size());
                    assertFalse(context.containsBean(SimpleXffCaptureWebConstant.FILTER_BEAN_NAME),
                            "关闭时不应注册 XFF Filter");
                    assertTrue(context.getBeansOfType(XffCaptureService.class).isEmpty(),
                            "关闭时不应注册 XFF Service");
                });
    }

    @Test
    void shouldRegisterOnlyRequestDispatcherWithServletContainer() throws Exception {
        SimpleXffCaptureConfiguration configuration = new SimpleXffCaptureConfiguration();
        XffCaptureService service = mock(XffCaptureService.class);
        FilterRegistrationBean<SimpleXffCaptureFilter> registration =
                configuration.simpleXffCaptureFilterRegistration(service, new SimpleXffCaptureProperties());
        ServletContext servletContext = mock(ServletContext.class);
        FilterRegistration.Dynamic dynamicRegistration = mock(FilterRegistration.Dynamic.class);
        when(servletContext.addFilter(eq(SimpleXffCaptureWebConstant.FILTER_NAME),
                any(SimpleXffCaptureFilter.class))).thenReturn(dynamicRegistration);

        registration.onStartup(servletContext);

        log.info("验证 Filter 向 Servlet 容器注册的 dispatcher 与 URL pattern");
        verify(dynamicRegistration).addMappingForUrlPatterns(
                eq(EnumSet.of(DispatcherType.REQUEST)), eq(false),
                eq(SimpleXffCaptureWebConstant.FILTER_URL_PATTERN));
    }

    @Test
    void shouldRegisterOneServiceAndRequestFilterWhenEnabled() {
        contextRunner.withPropertyValues(SimpleXffCaptureConstant.CONFIG_PREFIX + ".enable=true")
                .run(context -> {
                    FilterRegistrationBean<?> registration =
                            context.getBean(SimpleXffCaptureWebConstant.FILTER_BEAN_NAME, FilterRegistrationBean.class);

                    log.info("开启时 Service 数量：{}，默认 Filter order={}",
                            context.getBeansOfType(XffCaptureService.class).size(), registration.getOrder());
                    assertEquals(1, context.getBeansOfType(XffCaptureService.class).size(),
                            "开启时应精确注册一个 XFF Service");
                    assertTrue(registration.getFilter() instanceof SimpleXffCaptureFilter,
                            "应注册 XFF 自动采集 Filter");
                    assertEquals(DEFAULT_FILTER_ORDER, registration.getOrder(),
                            "未配置时 Filter order 应使用最低优先级默认值");
                    assertEquals(Collections.singleton(SimpleXffCaptureWebConstant.FILTER_URL_PATTERN),
                            registration.getUrlPatterns(), "Filter 应覆盖全部 URL");
                });
    }

    @Test
    void shouldBindExcludedPathPatternsWithoutChangingFilterUrlRange() {
        contextRunner.withPropertyValues(
                        SimpleXffCaptureConstant.CONFIG_PREFIX + ".enable=true",
                        SimpleXffCaptureConstant.CONFIG_PREFIX + "."
                                + SimpleXffCaptureConstant.CONFIG_EXCLUDED_PATH_PATTERNS + "[0]=/actuator/**")
                .run(context -> {
                    FilterRegistrationBean<?> registration =
                            context.getBean(SimpleXffCaptureWebConstant.FILTER_BEAN_NAME, FilterRegistrationBean.class);
                    SimpleXffCaptureProperties properties = context.getBean(SimpleXffCaptureProperties.class);

                    log.info("配置排除路径模式：{}，Filter URL 范围：{}",
                            properties.getExcludedPathPatterns(), registration.getUrlPatterns());
                    assertEquals(Collections.singletonList("/actuator/**"), properties.getExcludedPathPatterns(),
                            "配置属性应绑定排除路径模式");
                    assertEquals(Collections.singleton(SimpleXffCaptureWebConstant.FILTER_URL_PATTERN),
                            registration.getUrlPatterns(), "排除路径模式不应缩小 Filter URL 注册范围");
                });
    }

    @Test
    void shouldUseConfiguredFilterOrder() {
        contextRunner.withPropertyValues(
                        SimpleXffCaptureConstant.CONFIG_PREFIX + ".enable=true",
                        SimpleXffCaptureConstant.CONFIG_PREFIX + "."
                                + SimpleXffCaptureConstant.CONFIG_ORDER + "=" + CONFIGURED_FILTER_ORDER)
                .run(context -> {
                    FilterRegistrationBean<?> registration =
                            context.getBean(SimpleXffCaptureWebConstant.FILTER_BEAN_NAME, FilterRegistrationBean.class);
                    SimpleXffCaptureProperties properties = context.getBean(SimpleXffCaptureProperties.class);

                    log.info("自定义 Filter order：{}", registration.getOrder());
                    assertEquals(CONFIGURED_FILTER_ORDER, properties.getOrder(),
                            "配置属性应绑定自定义 Filter order");
                    assertEquals(CONFIGURED_FILTER_ORDER, registration.getOrder(),
                            "Filter 注册顺序应使用配置值");
                    assertEquals(1, context.getBeansOfType(XffCaptureService.class).size(),
                            "自定义 order 不应改变 XFF Service 注册");
                    assertTrue(registration.getFilter() instanceof SimpleXffCaptureFilter,
                            "自定义 order 不应改变 Filter 类型");
                    assertEquals(Collections.singleton(SimpleXffCaptureWebConstant.FILTER_URL_PATTERN),
                            registration.getUrlPatterns(), "自定义 order 不应改变 Filter URL 范围");
                });
    }
}
