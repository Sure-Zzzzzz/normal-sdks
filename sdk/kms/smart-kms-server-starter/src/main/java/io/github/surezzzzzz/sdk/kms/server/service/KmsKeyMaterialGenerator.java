package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;

/**
 * KMS 可信边界内的密钥材料生成端口。
 *
 * @author surezzzzzz
 */
public interface KmsKeyMaterialGenerator {

    /**
     * 为指定逻辑密钥生成活动版本材料。
     *
     * @param tenantId  逻辑密钥所属租户标识
     * @param keyRef    逻辑密钥稳定标识
     * @param version   待生成的版本号
     * @param algorithm 密码算法
     * @return 仅供 KMS 可信边界使用的活动密钥版本
     */
    KmsKeyVersion generate(String tenantId, String keyRef, int version, KmsAlgorithm algorithm);
}
