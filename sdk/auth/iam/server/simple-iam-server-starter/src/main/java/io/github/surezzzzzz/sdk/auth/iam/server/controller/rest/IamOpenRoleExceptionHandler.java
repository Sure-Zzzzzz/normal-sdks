package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRoleErrorResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleHttpHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * 仅接管新角色入口的安全拒绝和有界请求体，不改变旧业务错误体。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IamOpenRoleRestController.class)
public class IamOpenRoleExceptionHandler extends RequestBodyAdviceAdapter {
    /**
     * 限制作用在本控制器的明确 JSON 请求。
     */
    @Override
    public boolean supports(MethodParameter parameter, Type targetType, Class converterType) {
        return true;
    }

    /**
     * 解码前最多读取预算加一个字节，覆盖 Content-Length 未知或不可信的请求。
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                           Class converterType) throws IOException {
        if (input.getHeaders().getContentLength() > SimpleIamServerConstant.OPEN_ROLE_REQUEST_BYTE_BUDGET) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_BODY_TOO_LARGE);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[SimpleIamServerConstant.OPEN_ROLE_REQUEST_READ_BUFFER];
        InputStream stream = input.getBody();
        int count;
        while ((count = stream.read(buffer, 0, Math.min(buffer.length,
                SimpleIamServerConstant.OPEN_ROLE_REQUEST_BYTE_BUDGET - body.size() + 1))) != -1) {
            body.write(buffer, 0, count);
            if (body.size() > SimpleIamServerConstant.OPEN_ROLE_REQUEST_BYTE_BUDGET) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_BODY_TOO_LARGE);
            }
        }
        byte[] bytes = body.toByteArray();
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return input.getHeaders();
            }
        };
    }

    /**
     * 业务拒绝仅输出已映射安全消息。
     */
    @ExceptionHandler(SimpleIamServerException.class)
    public ResponseEntity<OpenRoleErrorResponse> business(SimpleIamServerException error) {
        return response(IamOpenRoleHttpHelper.status(error.getErrorCode()));
    }

    /**
     * API 或 DATA 不足失败关闭。
     */
    @ExceptionHandler({AccessDeniedException.class, DataPermissionAccessDeniedException.class})
    public ResponseEntity<OpenRoleErrorResponse> forbidden(Exception error) {
        return response(HttpStatus.FORBIDDEN);
    }

    /**
     * 唯一约束及当前锁协调冲突可重读，不泄露数据库异常。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<OpenRoleErrorResponse> conflict(Exception error) {
        return response(HttpStatus.CONFLICT);
    }

    /**
     * 数据库不可达、锁超时或死锁不能被当作执行成功。
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<OpenRoleErrorResponse> unavailable(Exception error) {
        return response(HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 格式、参数转换及校验错误不返回失败 200。
     */
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class})
    public ResponseEntity<OpenRoleErrorResponse> invalid(Exception error) {
        return response(HttpStatus.BAD_REQUEST);
    }

    /**
     * 未预期错误保留服务端诊断，但不向调用方暴露异常链。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenRoleErrorResponse> unexpected(Exception error) {
        log.error("开放角色内部错误", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<OpenRoleErrorResponse> response(HttpStatus status) {
        OpenRoleErrorResponse body = IamOpenRoleHttpHelper.error(status);
        log.debug("开放角色请求拒绝：requestId={}, status={}", body.getRequestId(), status.value());
        return ResponseEntity.status(status).body(body);
    }
}
