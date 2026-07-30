package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.LicenseTermType;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.support.LicenseValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collection;
import java.util.List;

/**
 * 功能集合条款。
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
public final class FeatureSetTerm implements LicenseTerm {

    private final List<String> features;

    /**
     * 创建功能集合条款。
     *
     * @param features 功能标识集合
     */
    public FeatureSetTerm(Collection<String> features) {
        this.features = LicenseValidationHelper.normalizeTexts(features, SmartLicenseCoreConstant.FIELD_FEATURES,
                SmartLicenseCoreConstant.MAX_FEATURE_COUNT, SmartLicenseCoreConstant.MAX_FEATURE_LENGTH);
        if (this.features.isEmpty()) {
            throw LicenseValidationHelper.validation(SmartLicenseCoreConstant.FIELD_FEATURES);
        }
    }

    /**
     * 获取功能集合条款类型代码。
     *
     * @return 功能集合条款类型代码
     */
    @Override
    public String getType() {
        return LicenseTermType.FEATURE_SET.getCode();
    }
}
