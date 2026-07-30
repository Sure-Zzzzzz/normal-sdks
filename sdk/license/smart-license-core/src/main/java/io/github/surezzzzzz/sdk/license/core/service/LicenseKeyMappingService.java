package io.github.surezzzzzz.sdk.license.core.service;

import io.github.surezzzzzz.sdk.license.core.model.LicenseKeyMapping;

/**
 * License 业务密钥映射服务。
 *
 * @author surezzzzzz
 */
public interface LicenseKeyMappingService {

    /**
     * 创建不可变业务 kid 映射。
     *
     * @param mapping 待创建映射
     * @return 经 KMS 公钥核验后的映射
     */
    LicenseKeyMapping create(LicenseKeyMapping mapping);
}
