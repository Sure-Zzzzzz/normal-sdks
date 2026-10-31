package io.github.surezzzzzz.sdk.license.server.web;

/**
 * License HTTP 业务异常：携带设计 §1 错误契约的状态码（401/403/404/400/409/413/422/503）。
 *
 * <p>异常消息即响应 message（不含异常类、SQL、上游正文）；处理器统一补 timestamp/requestId。</p>
 *
 * @author surezzzzzz
 */
public class LicenseHttpException extends RuntimeException {

    private final int status;

    public LicenseHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static LicenseHttpException unauthenticated() {
        return new LicenseHttpException(401, "未认证");
    }

    public static LicenseHttpException forbidden() {
        return new LicenseHttpException(403, "无权访问该资源");
    }

    public static LicenseHttpException notFound() {
        return new LicenseHttpException(404, "资源不存在或不可见");
    }

    public static LicenseHttpException badRequest(String message) {
        return new LicenseHttpException(400, message);
    }

    public static LicenseHttpException conflict(String message) {
        return new LicenseHttpException(409, message);
    }

    public static LicenseHttpException unprocessable(String message) {
        return new LicenseHttpException(422, message);
    }

    public static LicenseHttpException payloadTooLarge() {
        return new LicenseHttpException(413, "请求体超限");
    }

    public static LicenseHttpException dependencyUnavailable(String message) {
        return new LicenseHttpException(503, message);
    }

    public int getStatus() {
        return status;
    }
}
