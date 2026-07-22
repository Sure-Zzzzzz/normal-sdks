package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Management 页面异常处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = SmartRedisLimiterManagementPageController.class)
@SmartRedisLimiterManagementComponent
public class SmartRedisLimiterManagementPageExceptionHandler {

    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 构造页面异常处理器
     *
     * @param properties management 配置
     */
    public SmartRedisLimiterManagementPageExceptionHandler(
            SmartRedisLimiterManagementProperties properties) {
        this.properties = properties;
    }

    /**
     * 处理管理页面服务端异常
     *
     * @param exception 页面异常
     * @return 脱敏错误页面
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handlePageFailure(Exception exception) {
        log.error("SmartRedisLimiter Management 页面异常", exception);
        ModelAndView modelAndView = new ModelAndView(
                SmartRedisLimiterManagementConstant.VIEW_ERROR);
        modelAndView.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        modelAndView.addObject(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_MESSAGE,
                ErrorMessage.PAGE_UNAVAILABLE);
        String uiBasePath = properties.getUi().getBasePath();
        modelAndView.addObject(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_POLICY_PAGE_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE);
        modelAndView.addObject(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_BOOTSTRAP_CSS_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_BOOTSTRAP_CSS);
        modelAndView.addObject(SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_MANAGEMENT_UI_CSS_URL,
                uiBasePath + SmartRedisLimiterManagementConstant.PATH_MANAGEMENT_UI_CSS);
        return modelAndView;
    }
}
