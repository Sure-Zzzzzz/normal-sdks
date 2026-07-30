package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.LicenseTermType;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 容量条款。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class CapacityTerm implements LicenseTerm {

    private final String metric;
    private final long limit;

    /**
     * 创建容量条款。
     *
     * @param metric 容量指标
     * @param limit  容量上限
     */
    public CapacityTerm(String metric, long limit) {
        this.metric = LicenseValidationHelper.requireText(metric, SmartLicenseCoreConstant.FIELD_METRIC,
                SmartLicenseCoreConstant.MAX_METRIC_LENGTH);
        if (limit < SmartLicenseCoreConstant.ZERO) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_LIMIT);
        }
        this.limit = limit;
    }

    /**
     * 获取容量条款类型代码。
     *
     * @return 容量条款类型代码
     */
    @Override
    public String getType() {
        return LicenseTermType.CAPACITY.getCode();
    }
}
