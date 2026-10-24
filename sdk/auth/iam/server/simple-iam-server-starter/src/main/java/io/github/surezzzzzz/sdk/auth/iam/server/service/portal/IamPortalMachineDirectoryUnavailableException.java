package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

/**
 * Portal 机器目录存量映射无法安全投影时的内部异常。
 *
 * <p>该异常故意不复用 OPEN_ROLE_UNAVAILABLE；目录数据损坏是 IAM 服务内部错误，
 * 应由统一兜底映射为 HTTP 500，而不是误报为依赖暂不可用的 503。</p>
 */
public class IamPortalMachineDirectoryUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IamPortalMachineDirectoryUnavailableException(String message) {
        super(message);
    }
}
