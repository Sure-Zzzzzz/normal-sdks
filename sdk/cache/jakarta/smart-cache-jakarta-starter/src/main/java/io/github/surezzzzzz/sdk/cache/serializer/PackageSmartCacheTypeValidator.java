package io.github.surezzzzzz.sdk.cache.serializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于包名前缀的类型校验器
 *
 * @author surezzzzzz
 */
public class PackageSmartCacheTypeValidator implements SmartCacheTypeValidator {

    private final List<String> trustedPackages;

    public PackageSmartCacheTypeValidator(List<String> trustedPackages) {
        if (trustedPackages == null) {
            this.trustedPackages = Collections.emptyList();
        } else {
            this.trustedPackages = new ArrayList<>(trustedPackages);
        }
    }

    @Override
    public boolean isTrusted(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return false;
        }
        for (String trustedPackage : trustedPackages) {
            if (trustedPackage == null || trustedPackage.trim().isEmpty()) {
                continue;
            }
            if ("*".equals(trustedPackage) || typeName.equals(trustedPackage)
                    || typeName.startsWith(trustedPackage + ".")) {
                return true;
            }
        }
        return false;
    }
}
