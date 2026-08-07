package io.github.surezzzzzz.sdk.auth.authorization.application.core.spi;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;

/**
 * 应用 API 授权判定器。
 *
 * @author surezzzzzz
 */
public interface ApplicationAuthorizationEvaluator {

    /**
     * 评估主体对应用精确 API 权限的访问。
     *
     * @param context         已验证的应用授权上下文
     * @param applicationCode 目标应用标识
     * @param permissionCode  精确 API 权限码
     * @return 授权判定
     */
    ApplicationAuthorizationDecision evaluateApi(ApplicationAuthorizationContext context, String applicationCode,
                                                 String permissionCode);
}
