package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.OpenRoleErrorResponse;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.UUID;

/**
 * 开放角色及旧管理入口的新错误映射，不输出实现异常文本。
 *
 * @author surezzzzzz
 */
public final class IamOpenRoleHttpHelper {
    private IamOpenRoleHttpHelper() {
    }

    /**
     * 根据内部错误码映射语义正确的 HTTP 状态。
     */
    public static HttpStatus status(String code) {
        if (ErrorCode.OPEN_ROLE_AUTHENTICATION_REQUIRED.equals(code)) return HttpStatus.UNAUTHORIZED;
        if (ErrorCode.OPEN_ROLE_FORBIDDEN.equals(code)) return HttpStatus.FORBIDDEN;
        if (ErrorCode.OPEN_ROLE_NOT_FOUND.equals(code) || ErrorCode.APPLICATION_MANIFEST_NOT_FOUND.equals(code)
                || ErrorCode.TRUSTED_APPLICATION_NOT_FOUND.equals(code)) return HttpStatus.NOT_FOUND;
        if (ErrorCode.OPEN_ROLE_CONFLICT.equals(code) || ErrorCode.APPLICATION_MANIFEST_CONFLICT.equals(code)
                || ErrorCode.TRUSTED_APPLICATION_MUTATION_BLOCKED.equals(code)) return HttpStatus.CONFLICT;
        if (ErrorCode.OPEN_ROLE_PRECONDITION_REQUIRED.equals(code)) return HttpStatus.PRECONDITION_REQUIRED;
        if (ErrorCode.OPEN_ROLE_PRECONDITION_FAILED.equals(code)) return HttpStatus.PRECONDITION_FAILED;
        if (ErrorCode.OPEN_DIRECTORY_BUDGET_EXCEEDED.equals(code)) return HttpStatus.UNPROCESSABLE_ENTITY;
        if (ErrorCode.OPEN_ROLE_UNAVAILABLE.equals(code)) return HttpStatus.SERVICE_UNAVAILABLE;
        if (ErrorCode.OPEN_ROLE_BODY_TOO_LARGE.equals(code)) return HttpStatus.PAYLOAD_TOO_LARGE;
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 安全消息与状态对应，不复用可能含人员资料、SQL 或异常链的 message。
     */
    public static String message(HttpStatus status) {
        switch (status) {
            case UNAUTHORIZED:
                return ServerErrorMessage.OPEN_ROLE_AUTHENTICATION_REQUIRED;
            case FORBIDDEN:
                return ServerErrorMessage.OPEN_ROLE_FORBIDDEN;
            case NOT_FOUND:
                return ServerErrorMessage.OPEN_ROLE_NOT_FOUND;
            case CONFLICT:
                return ServerErrorMessage.OPEN_ROLE_CONFLICT;
            case PRECONDITION_REQUIRED:
                return ServerErrorMessage.OPEN_ROLE_PRECONDITION_REQUIRED;
            case PRECONDITION_FAILED:
                return ServerErrorMessage.OPEN_ROLE_PRECONDITION_FAILED;
            case UNPROCESSABLE_ENTITY:
                return ServerErrorMessage.OPEN_DIRECTORY_BUDGET_EXCEEDED;
            case SERVICE_UNAVAILABLE:
                return ServerErrorMessage.OPEN_ROLE_UNAVAILABLE;
            case PAYLOAD_TOO_LARGE:
                return ServerErrorMessage.OPEN_ROLE_BODY_TOO_LARGE;
            case METHOD_NOT_ALLOWED:
                return ServerErrorMessage.OPEN_ROLE_HTTP_METHOD_INVALID;
            case UNSUPPORTED_MEDIA_TYPE:
                return ServerErrorMessage.OPEN_ROLE_HTTP_MEDIA_INVALID;
            case INTERNAL_SERVER_ERROR:
                return ServerErrorMessage.OPEN_ROLE_HTTP_INTERNAL_ERROR;
            default:
                return ServerErrorMessage.OPEN_ROLE_INVALID;
        }
    }

    /**
     * 认证后的请求标识优先使用已验证上下文；其他错误生成独立关联标识。
     */
    public static OpenRoleErrorResponse error(HttpStatus status) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String requestId = auth != null && auth.getPrincipal() instanceof VerifiedResourceContext
                ? ((VerifiedResourceContext) auth.getPrincipal()).getRequestId() : UUID.randomUUID().toString();
        return new OpenRoleErrorResponse(message(status), Instant.now().toString(), requestId);
    }
}
