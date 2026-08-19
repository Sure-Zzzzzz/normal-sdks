package io.github.surezzzzzz.sdk.auth.aksk.server.exception;

/**
 * 应用授权投影不存在异常。
 *
 * @author surezzzzzz
 */
public class ApplicationAuthorizationNotFoundException extends SimpleAkskServerException {

    public ApplicationAuthorizationNotFoundException() {
        super("应用授权不存在");
    }
}
