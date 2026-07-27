package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.controller;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.annotation.SimpleKafkaOutboxManagementComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletResponse;

/**
 * Outbox Management 页面异常处理。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@ControllerAdvice(assignableTypes = KafkaOutboxManagementPageController.class)
@Order
@SimpleKafkaOutboxManagementComponent
public class KafkaOutboxManagementPageExceptionHandler {
    private final SimpleKafkaOutboxManagementProperties properties;

    /**
     * 处理页面异常。
     */
    @ExceptionHandler(KafkaOutboxManagementException.class)
    public String handleManagementException(KafkaOutboxManagementException exception, Model model,
                                            HttpServletResponse response) {
        if (ErrorCode.RECORD_NOT_FOUND.equals(exception.getErrorCode())) return error(model, response,
                HttpServletResponse.SC_NOT_FOUND, "Outbox 记录不存在");
        if (ErrorCode.RECORD_STATE_CONFLICT.equals(exception.getErrorCode())) return error(model, response,
                HttpServletResponse.SC_CONFLICT, "Outbox 记录当前状态不允许该操作");
        if (ErrorCode.REQUEST_INVALID.equals(exception.getErrorCode())) return error(model, response,
                HttpServletResponse.SC_BAD_REQUEST, "页面请求参数无效");
        if (ErrorCode.PERSISTENCE_FAILED.equals(exception.getErrorCode())) {
            log.error("Outbox Management 页面持久化操作失败，错误码：{}", exception.getErrorCode(), exception);
        } else {
            log.warn("Outbox Management 页面操作失败，错误码：{}", exception.getErrorCode());
        }
        return error(model, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "页面暂时不可用，请稍后重试");
    }

    /**
     * 处理未预期页面异常。
     */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception exception, Model model, HttpServletResponse response) {
        log.error("Outbox Management 页面发生未预期异常", exception);
        return error(model, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "页面暂时不可用，请稍后重试");
    }

    /**
     * 返回指定状态的安全错误页。
     */
    private String error(Model model, HttpServletResponse response, int status, String message) {
        String basePath = properties.getUi().getBasePath();
        response.setStatus(status);
        model.addAttribute("message", message);
        model.addAttribute("dashboardUrl", basePath + "/");
        model.addAttribute("bootstrapCssUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_BOOTSTRAP_CSS);
        model.addAttribute("managementUiCssUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_MANAGEMENT_UI_CSS);
        model.addAttribute("brandIconUrl", basePath + SimpleKafkaOutboxManagementConstant.PATH_ASSETS + "/icon.svg");
        return SimpleKafkaOutboxManagementConstant.VIEW_ERROR;
    }
}
