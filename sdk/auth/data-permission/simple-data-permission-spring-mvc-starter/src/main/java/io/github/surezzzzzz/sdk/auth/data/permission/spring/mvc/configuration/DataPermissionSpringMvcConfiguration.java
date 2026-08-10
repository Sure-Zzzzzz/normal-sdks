package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.configuration;

import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.interceptor.DataPermissionOperationInterceptor;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.resolver.CurrentDataAccessPlanArgumentResolver;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC数据权限配置。
 *
 * @author surezzzzzz
 */
public final class DataPermissionSpringMvcConfiguration implements WebMvcConfigurer, Ordered {

    private final DataPermissionFacade facade;

    /**
     * 创建Spring MVC数据权限配置。
     *
     * @param facade 数据权限门面
     */
    public DataPermissionSpringMvcConfiguration(DataPermissionFacade facade) {
        this.facade = facade;
    }

    /**
     * 注册数据权限操作拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DataPermissionOperationInterceptor(facade)).order(Ordered.LOWEST_PRECEDENCE);
    }

    /**
     * 注册数据访问计划参数解析器。
     *
     * @param resolvers 参数解析器集合
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentDataAccessPlanArgumentResolver());
    }

    /**
     * 在API权限拦截器之后注册配置。
     *
     * @return MVC配置顺序
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
