package io.github.surezzzzzz.sdk.license.core.exception;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * Core 2 统一异常：携带错误分类编码与安全消息。
 *
 * <p>消息固定来自 {@link ErrorMessage}，不允许调用方把原始协议字节、编号值或设备数据拼进异常文本。</p>
 *
 * @author surezzzzzz
 */
public class LicenseCoreException extends RuntimeException {

    private final ErrorCode errorCode;

    public LicenseCoreException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
