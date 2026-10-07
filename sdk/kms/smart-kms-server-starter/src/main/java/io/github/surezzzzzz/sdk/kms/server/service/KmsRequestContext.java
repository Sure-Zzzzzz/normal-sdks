package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Getter;

/**
 * 已认证 KMS 请求上下文。
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsRequestContext {

    /**
     * 已认证主体。
     */
    private final KmsPrincipal principal;
    /**
     * 请求关联标识。
     */
    private final String requestId;
    /**
     * 可信认证解析器已确认当前主体为人员；旧解析器默认没有此证明。
     */
    private final boolean verifiedHumanSubject;

    /**
     * 创建已认证请求上下文。
     *
     * @param principal 已认证主体
     * @param requestId 请求关联标识
     */
    public KmsRequestContext(KmsPrincipal principal, String requestId) {
        this(principal, requestId, false);
    }

    private KmsRequestContext(KmsPrincipal principal, String requestId, boolean verifiedHumanSubject) {
        if (principal == null) {
            throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
        }
        this.principal = principal;
        this.requestId = KmsValidationHelper.requireRequestId(requestId);
        this.verifiedHumanSubject = verifiedHumanSubject;
    }

    /**
     * 从可信认证器已经验证的人员身份建立本人上下文，不接受请求字段作为人员证明。
     *
     * @param principal 已认证且未切换治理归属的人员主体
     * @param requestId 请求关联标识
     * @return 带人员证明的本人上下文
     */
    public static KmsRequestContext forVerifiedHuman(KmsPrincipal principal, String requestId) {
        if (principal == null || !principal.getPrincipalId().equals(principal.getOwnerPrincipalId())) {
            throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
        }
        return new KmsRequestContext(principal, requestId, true);
    }
}
