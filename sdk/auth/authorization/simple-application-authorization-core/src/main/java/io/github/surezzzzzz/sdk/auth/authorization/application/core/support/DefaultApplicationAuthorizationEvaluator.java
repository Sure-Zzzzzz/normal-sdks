package io.github.surezzzzzz.sdk.auth.authorization.application.core.support;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.spi.ApplicationAuthorizationEvaluator;

import java.time.Clock;
import java.time.Duration;
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
     * 时钟容差：允许判定时刻早于签发时刻的最大时长，覆盖签发端时间戳秒级取整偏差与跨机部署时钟偏差。
     */
    private final Duration clockSkew;

    /**
     * 使用 UTC 系统时钟与默认时钟容差创建判定器。
     */
    public DefaultApplicationAuthorizationEvaluator() {
        this(Clock.systemUTC());
    }

    /**
     * 使用指定时钟与默认时钟容差创建判定器。
     *
     * @param clock 判定时钟
     */
    public DefaultApplicationAuthorizationEvaluator(Clock clock) {
        this(clock, SimpleApplicationAuthorizationConstant.DEFAULT_CLOCK_SKEW);
    }

    /**
     * 使用指定时钟与时钟容差创建判定器。
     *
     * <p>容差只放宽时效下界（判定时刻最多允许早于签发时刻 {@code clockSkew}），
     * 不放宽到期上界。{@code Duration.ZERO} 表示恢复 1.0.0 的零容差严格模式。
     *
     * @param clock     判定时钟
     * @param clockSkew 时钟容差，非负
     */
    public DefaultApplicationAuthorizationEvaluator(Clock clock, Duration clockSkew) {
        if (clock == null) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(
                    SimpleApplicationAuthorizationConstant.DETAIL_CLOCK_CANNOT_BE_NULL);
        }
        if (clockSkew == null || clockSkew.isNegative()) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(
                    SimpleApplicationAuthorizationConstant.DETAIL_CLOCK_SKEW_INVALID);
        }
        this.clock = clock;
        this.clockSkew = clockSkew;
    }

    /**
     * 按应用准入、时效和精确 API 权限进行授权判定。
     *
     * <p>时效下界带时钟容差：判定时刻早于 {@code issuedAt - clockSkew} 才拒绝；
     * 到期上界零容差：判定时刻到达 {@code expiresAt} 即拒绝，不延长有效期。
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
        if (now.isBefore(context.getIssuedAt().minus(clockSkew)) || !now.isBefore(context.getExpiresAt())) {
            return ApplicationAuthorizationDecision.DENY;
        }
        if (!context.getApplicationCode().equals(applicationCode)) {
            return ApplicationAuthorizationDecision.DENY;
        }
        return context.getApiPermissions().contains(permissionCode)
                ? ApplicationAuthorizationDecision.ALLOW : ApplicationAuthorizationDecision.DENY;
    }
}
