package io.github.surezzzzzz.sdk.kms.server.exception;

/**
 * 已认证主体解析失败时使用的安全异常。
 *
 * @author surezzzzzz
 */
public class KmsUnauthenticatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 固定 Bearer 认证挑战。
     */
    public KmsUnauthenticatedException() {
    }

    /**
     * 获取响应使用的固定认证挑战值。
     */
    public String getWwwAuthenticate() {
        return "Bearer";
    }
}
