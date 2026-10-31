package io.github.surezzzzzz.sdk.license.server.web;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一错误契约（DESIGN.HTTP.1.md §1）：仅 message/timestamp/requestId。
 * 无业务 code、异常类、SQL、上游正文或隐藏目标信息。
 *
 * <p>由 LicenseServerHttpAutoConfiguration 显式 @Import（宿主组件扫描不覆盖 starter 包）。</p>
 *
 * @author surezzzzzz
 */
@RestControllerAdvice
@SimpleLicenseServerComponent
public class LicenseHttpExceptionHandler {

    @ExceptionHandler(LicenseHttpException.class)
    public ResponseEntity<Map<String, Object>> handle(LicenseHttpException failure,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(failure.getStatus()).body(body(failure.getMessage(), request));
    }

    /** 基础设施故障兜底 503：不向调用方暴露细节；日志保留最小分类用于排障。 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(RuntimeException failure,
                                                                HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(LicenseHttpExceptionHandler.class).warn(
                "业务兜底 503：requestId={}, 异常类型={}",
                request.getAttribute("licenseRequestId"),
                failure.getClass().getName());
        return ResponseEntity.status(503).body(body("服务暂不可用", request));
    }

    /** Spring MVC 在进入 Controller 前拒绝请求时，也必须保持 License 的 JSON 错误契约。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException failure, HttpServletRequest request) {
        return ResponseEntity.status(405).body(body("请求方法不支持", request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException failure, HttpServletRequest request) {
        return ResponseEntity.status(415).body(body("请求媒体类型不支持", request));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException failure, HttpServletRequest request) {
        return ResponseEntity.status(406).body(body("响应媒体类型不可接受", request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
            HttpMessageNotReadableException failure, HttpServletRequest request) {
        return ResponseEntity.status(400).body(body("请求体格式无效", request));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            MissingServletRequestParameterException failure, HttpServletRequest request) {
        return ResponseEntity.status(400).body(body("请求参数缺失", request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleArgumentNotValid(
            MethodArgumentNotValidException failure, HttpServletRequest request) {
        return ResponseEntity.status(422).body(body("请求参数校验失败", request));
    }

    private static Map<String, Object> body(String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        Object requestId = request.getAttribute("licenseRequestId");
        body.put("requestId", requestId == null ? "" : String.valueOf(requestId));
        return body;
    }
}
