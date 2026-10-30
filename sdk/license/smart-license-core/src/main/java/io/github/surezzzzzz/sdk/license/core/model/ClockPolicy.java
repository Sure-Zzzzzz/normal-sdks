package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;

/**
 * 时钟政策：skewSeconds 与 rollbackToleranceSeconds 各 0..300 整数秒。
 *
 * <p>skew 只容忍 iat 等可信时间与本机小误差，不对商业/租约截止加秒。大幅回退进
 * CLOCK_SUSPECT 不进宽限。</p>
 *
 * @author surezzzzzz
 */
public final class ClockPolicy {

    private final int skewSeconds;
    private final int rollbackToleranceSeconds;

    private ClockPolicy(int skewSeconds, int rollbackToleranceSeconds) {
        if (skewSeconds < 0 || skewSeconds > SmartLicenseCoreConstant.CLOCK_POLICY_SECONDS_MAX
                || rollbackToleranceSeconds < 0
                || rollbackToleranceSeconds > SmartLicenseCoreConstant.CLOCK_POLICY_SECONDS_MAX) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        this.skewSeconds = skewSeconds;
        this.rollbackToleranceSeconds = rollbackToleranceSeconds;
    }

    public static ClockPolicy of(int skewSeconds, int rollbackToleranceSeconds) {
        return new ClockPolicy(skewSeconds, rollbackToleranceSeconds);
    }

    public int getSkewSeconds() {
        return skewSeconds;
    }

    public int getRollbackToleranceSeconds() {
        return rollbackToleranceSeconds;
    }
}
