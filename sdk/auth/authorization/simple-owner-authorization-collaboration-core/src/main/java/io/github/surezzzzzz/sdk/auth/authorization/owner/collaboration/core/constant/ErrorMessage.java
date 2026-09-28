package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant;

/**
 * 所属人授权协作错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 协作模型无效。
     */
    public static final String INVALID_OWNER_AUTHORIZATION_MODEL = "所属人授权协作模型无效：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleOwnerAuthorizationCollaborationConstant
                .MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
