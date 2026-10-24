package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRoleErrorResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMachineDirectoryUnavailableException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleHttpHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Portal 机器目录只返回不可缓存的标准错误体，不改变其他开放 API 的错误语义。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = IamPortalMachineApplicationOpenApiController.class)
public class IamPortalMachineApplicationExceptionHandler {

    @ExceptionHandler(SimpleIamServerException.class)
    public ResponseEntity<OpenRoleErrorResponse> business(SimpleIamServerException error) {
        return response(IamOpenRoleHttpHelper.status(error.getErrorCode()));
    }

    @ExceptionHandler({AccessDeniedException.class, DataPermissionAccessDeniedException.class})
    public ResponseEntity<OpenRoleErrorResponse> forbidden(Exception error) {
        return response(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<OpenRoleErrorResponse> invalid(Exception error) {
        return response(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<OpenRoleErrorResponse> methodNotAllowed(Exception error) {
        return response(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<OpenRoleErrorResponse> unsupportedMedia(Exception error) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * 存量路由投影损坏属于 IAM 自身数据错误，不能伪装成依赖暂不可用。
     */
    @ExceptionHandler(IamPortalMachineDirectoryUnavailableException.class)
    public ResponseEntity<OpenRoleErrorResponse> corruptedProjection(
            IamPortalMachineDirectoryUnavailableException error) {
        log.error("Portal 可信应用路由投影存量损坏", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenRoleErrorResponse> unexpected(Exception error) {
        log.error("Portal 可信应用路由投影处理失败", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<OpenRoleErrorResponse> response(HttpStatus status) {
        OpenRoleErrorResponse body = IamOpenRoleHttpHelper.error(status);
        log.debug("Portal 可信应用路由投影请求拒绝：requestId={}, status={}", body.getRequestId(), status.value());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
