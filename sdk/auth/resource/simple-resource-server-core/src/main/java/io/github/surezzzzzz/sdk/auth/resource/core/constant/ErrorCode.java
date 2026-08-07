package io.github.surezzzzzz.sdk.auth.resource.core.constant;

/**
 * 资源服务核心错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 无效资源认证模型。
     */
    public static final String INVALID_RESOURCE_AUTHENTICATION_MODEL = "BIZ_001";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleResourceServerConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
