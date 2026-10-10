package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.resolver;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.interceptor.DataPermissionOperationInterceptor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;

/**
 * 注入当前请求已评估的数据访问计划。
 *
 * @author surezzzzzz
 */
public final class CurrentDataAccessPlanArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 判断是否支持数据访问计划参数。
     *
     * @param parameter Controller参数
     * @return 是否支持
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 1.1.0 起双认：core 契约注解（新代码）与 legacy 本包注解（存量签名）等价注入
        return parameter.hasParameterAnnotation(CurrentDataAccessPlan.class)
                || parameter.hasParameterAnnotation(
                io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan.class);
    }

    /**
     * 解析当前请求数据访问计划。
     *
     * @param parameter     Controller参数
     * @param mavContainer  模型视图容器
     * @param webRequest    Web请求
     * @param binderFactory 绑定工厂
     * @return 已评估的数据访问计划
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (!DataAccessPlan.class.equals(parameter.getParameterType())) {
            throw new DataPermissionAccessDeniedException("数据访问计划参数类型无效");
        }
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Object value = request == null ? null : request.getAttribute(
                DataPermissionOperationInterceptor.ATTRIBUTE_DATA_ACCESS_PLAN);
        if (!(value instanceof DataAccessPlan)) {
            throw new DataPermissionAccessDeniedException("当前请求未评估数据访问计划");
        }
        return value;
    }
}
