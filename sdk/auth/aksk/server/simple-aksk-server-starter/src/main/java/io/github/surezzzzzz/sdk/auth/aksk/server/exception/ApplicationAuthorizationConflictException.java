package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

/**
 * 应用授权投影冲突异常。
 *
 * @author surezzzzzz
 */
public class ApplicationAuthorizationConflictException extends SimpleAkskServerException {

    public ApplicationAuthorizationConflictException() {
        super("应用授权已存在");
    }
}
