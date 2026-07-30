package io.github.surezzzzzz.sdk.crm.server.core.error;

import lombok.Getter;

/**
 * CRM 领域异常。
 *
 * @author surezzzzzz
 */
@Getter
public class CrmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CrmErrorCode errorCode;

    /**
     * 创建CrmException。
     *
     * @param errorCode CRM 错误码
     * @param message   异常说明
     *
     */
    public CrmException(CrmErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建CrmException。
     *
     * @param errorCode CRM 错误码
     * @param message   异常说明
     * @param cause     原始异常原因
     *
     */
    public CrmException(CrmErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 创建参数校验领域异常。
     *
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static CrmException validation(String field) {
        return new CrmException(CrmErrorCode.VALIDATION_FAILED, field);
    }

}
