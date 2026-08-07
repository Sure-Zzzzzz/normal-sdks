package io.github.surezzzzzz.sdk.auth.resource.core.constant;

/**
 * 资源服务核心错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 无效资源认证模型。
     */
    public static final String INVALID_RESOURCE_AUTHENTICATION_MODEL = "资源认证模型无效：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleResourceServerConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
