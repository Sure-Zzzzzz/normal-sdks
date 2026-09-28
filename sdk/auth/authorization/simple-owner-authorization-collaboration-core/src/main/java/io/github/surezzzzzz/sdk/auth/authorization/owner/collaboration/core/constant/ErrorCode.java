package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant;

/**
 * 所属人授权协作错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 协作模型无效。
     */
    public static final String INVALID_OWNER_AUTHORIZATION_MODEL = "BIZ_001";

    private ErrorCode() {
        throw new UnsupportedOperationException(SimpleOwnerAuthorizationCollaborationConstant
                .MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
