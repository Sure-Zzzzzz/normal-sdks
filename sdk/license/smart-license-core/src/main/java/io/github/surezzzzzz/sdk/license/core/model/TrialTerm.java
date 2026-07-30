package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.LicenseTermType;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 试用条款。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class TrialTerm implements LicenseTerm {

    private final boolean startOnActivation;
    private final int durationDays;

    /**
     * 创建试用条款。
     *
     * @param startOnActivation 是否从激活开始计时
     * @param durationDays      试用天数
     */
    public TrialTerm(boolean startOnActivation, int durationDays) {
        if (durationDays < SmartLicenseCoreConstant.MIN_POSITIVE_INTEGER) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_DURATION_DAYS);
        }
        this.startOnActivation = startOnActivation;
        this.durationDays = durationDays;
    }

    /**
     * 获取试用条款类型代码。
     *
     * @return 试用条款类型代码
     */
    @Override
    public String getType() {
        return LicenseTermType.TRIAL.getCode();
    }
}
