package io.github.surezzzzzz.sdk.feedback.server.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 控制器匹配前的反馈 413/415 错误响应；其他路径留给宿主异常解析链。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class FeedbackPreControllerExceptionResolver implements HandlerExceptionResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SimpleFeedbackServerProperties properties;

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception exception) {
        if (!isFeedbackRequest(request)) {
            return null;
        }
        HttpStatus status;
        String message;
        if (exception instanceof MaxUploadSizeExceededException) {
            status = HttpStatus.PAYLOAD_TOO_LARGE;
            message = ErrorMessage.REQUEST_TOO_LARGE;
        } else if (exception instanceof HttpMediaTypeNotSupportedException) {
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            message = ErrorMessage.UNSUPPORTED_MEDIA_TYPE;
        } else {
            return null;
        }
        ResponseEntity<Map<String, Object>> error = FeedbackRestExceptionHandler.build(status, message);
        byte[] body;
        try {
            body = OBJECT_MAPPER.writeValueAsBytes(error.getBody());
        } catch (JsonProcessingException serializationFailure) {
            log.error("反馈请求错误响应序列化失败 httpStatus={} stack={}", status.value(),
                    FeedbackRestExceptionHandler.safeStackSummary(serializationFailure));
            return null;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Correlation-Id", error.getHeaders().getFirst("X-Correlation-Id"));
        try {
            response.getOutputStream().write(body);
        } catch (IOException writeFailure) {
            log.warn("反馈请求错误响应写出失败 httpStatus={} stack={}", status.value(),
                    FeedbackRestExceptionHandler.safeStackSummary(writeFailure));
        }
        return new ModelAndView();
    }

    private boolean isFeedbackRequest(HttpServletRequest request) {
        String basePath = properties.getApiBasePath();
        if (basePath == null || basePath.isEmpty()) {
            return false;
        }
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(contextPath)) {
            return false;
        }
        String path = requestUri.substring(contextPath.length());
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }
}
