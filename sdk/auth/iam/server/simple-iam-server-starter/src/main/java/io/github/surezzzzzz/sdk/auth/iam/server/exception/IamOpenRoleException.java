package io.github.surezzzzzz.sdk.auth.iam.server.exception;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;

/**
 * 受委托角色的安全拒绝，不携带输入内容、SQL 或实现细节。
 *
 * @author surezzzzzz
 */
public class IamOpenRoleException extends SimpleIamServerException {
    /**
     * 以集中错误码构造安全异常。
     */
    public IamOpenRoleException(String errorCode) {
        super(errorCode, message(errorCode));
    }

    private static String message(String code) {
        if (ErrorCode.OPEN_ROLE_INVALID.equals(code)) return ServerErrorMessage.OPEN_ROLE_INVALID;
        if (ErrorCode.OPEN_ROLE_AUTHENTICATION_REQUIRED.equals(code))
            return ServerErrorMessage.OPEN_ROLE_AUTHENTICATION_REQUIRED;
        if (ErrorCode.OPEN_ROLE_FORBIDDEN.equals(code)) return ServerErrorMessage.OPEN_ROLE_FORBIDDEN;
        if (ErrorCode.OPEN_ROLE_NOT_FOUND.equals(code)) return ServerErrorMessage.OPEN_ROLE_NOT_FOUND;
        if (ErrorCode.OPEN_ROLE_PRECONDITION_REQUIRED.equals(code))
            return ServerErrorMessage.OPEN_ROLE_PRECONDITION_REQUIRED;
        if (ErrorCode.OPEN_ROLE_PRECONDITION_FAILED.equals(code))
            return ServerErrorMessage.OPEN_ROLE_PRECONDITION_FAILED;
        if (ErrorCode.OPEN_DIRECTORY_BUDGET_EXCEEDED.equals(code))
            return ServerErrorMessage.OPEN_DIRECTORY_BUDGET_EXCEEDED;
        if (ErrorCode.OPEN_ROLE_UNAVAILABLE.equals(code)) return ServerErrorMessage.OPEN_ROLE_UNAVAILABLE;
        if (ErrorCode.OPEN_ROLE_BODY_TOO_LARGE.equals(code)) return ServerErrorMessage.OPEN_ROLE_BODY_TOO_LARGE;
        return ServerErrorMessage.OPEN_ROLE_CONFLICT;
    }
}
