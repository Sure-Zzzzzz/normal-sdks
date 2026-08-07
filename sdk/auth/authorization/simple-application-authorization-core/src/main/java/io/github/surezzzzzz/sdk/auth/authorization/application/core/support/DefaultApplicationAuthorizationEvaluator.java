package io.github.surezzzzzz.sdk.auth.authorization.application.core.support;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.spi.ApplicationAuthorizationEvaluator;

import java.time.Clock;
import java.time.Instant;

/**
 * 默认应用 API 授权判定器。
 *
 * @author surezzzzzz
 */
public final class DefaultApplicationAuthorizationEvaluator implements ApplicationAuthorizationEvaluator {

    /**
     * 判定时钟。
     */
    private final Clock clock;

    /**
     * 使用 UTC 系统时钟创建判定器。
     */
    public DefaultApplicationAuthorizationEvaluator() {
        this(Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建判定器。
     *
     * @param clock 判定时钟
     */
    public DefaultApplicationAuthorizationEvaluator(Clock clock) {
        if (clock == null) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(
                    io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant.DETAIL_CLOCK_CANNOT_BE_NULL);
        }
        this.clock = clock;
    }

    /**
     * 按应用准入、时效和精确 API 权限进行授权判定。
     *
     * @param context         已验证的应用授权上下文
     * @param applicationCode 目标应用标识
     * @param permissionCode  精确 API 权限码
     * @return 授权判定
     */
    @Override
    public ApplicationAuthorizationDecision evaluateApi(ApplicationAuthorizationContext context, String applicationCode,
                                                        String permissionCode) {
        if (context == null || applicationCode == null || permissionCode == null || !context.isAdmitted()) {
            return ApplicationAuthorizationDecision.DENY;
        }
        Instant now = clock.instant();
        if (now.isBefore(context.getIssuedAt()) || !now.isBefore(context.getExpiresAt())) {
            return ApplicationAuthorizationDecision.DENY;
        }
        if (!context.getApplicationCode().equals(applicationCode)) {
            return ApplicationAuthorizationDecision.DENY;
        }
        return context.getApiPermissions().contains(permissionCode)
                ? ApplicationAuthorizationDecision.ALLOW : ApplicationAuthorizationDecision.DENY;
    }
}
