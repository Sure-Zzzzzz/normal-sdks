package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.support.KmsKeyMaterialHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

/**
 * 基于 JCA 生成 KMS 内部材料。
 *
 * @author surezzzzzz
 */
public class JcaKmsKeyMaterialGenerator implements KmsKeyMaterialGenerator {

    /**
     * 生成密钥材料的安全随机源。
     */
    private final SecureRandom secureRandom;

    /**
     * 创建 JCA 材料生成器。
     *
     * @param secureRandom KMS 可信边界使用的安全随机源
     */
    public JcaKmsKeyMaterialGenerator(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new KmsCryptoException();
        }
        this.secureRandom = secureRandom;
    }

    /**
     * 生成 ES256 私钥与公钥材料。
     */
    private static KmsKeyVersion generateEs256(String tenantId, String keyRef, int version,
                                               KmsAlgorithm algorithm, SecureRandom secureRandom) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(SmartKmsServerConstant.JCA_EC_KEY_PAIR_ALGORITHM);
        generator.initialize(new ECGenParameterSpec(SmartKmsServerConstant.JCA_ES256_CURVE_NAME), secureRandom);
        KeyPair keyPair = generator.generateKeyPair();
        KmsKeyVersion keyVersion = new KmsKeyVersion(tenantId, keyRef, version, algorithm,
                KmsKeyVersionState.ACTIVE, null, keyPair.getPrivate().getEncoded(), null,
                keyPair.getPublic().getEncoded(), null);
        KmsKeyMaterialHelper.validate(keyVersion);
        return keyVersion;
    }

    /**
     * 生成固定长度的 AES-256-GCM 对称材料。
     */
    private static KmsKeyVersion generateAes256Gcm(String tenantId, String keyRef, int version,
                                                   KmsAlgorithm algorithm, SecureRandom secureRandom) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(SmartKmsServerConstant.JCA_AES_KEY_ALGORITHM);
        generator.init(SmartKmsServerConstant.JCA_AES_256_KEY_SIZE_BITS, secureRandom);
        SecretKey secretKey = generator.generateKey();
        KmsKeyVersion keyVersion = new KmsKeyVersion(tenantId, keyRef, version, algorithm,
                KmsKeyVersionState.ACTIVE, null, null, secretKey.getEncoded(), null, null);
        KmsKeyMaterialHelper.validate(keyVersion);
        return keyVersion;
    }

    /**
     * 为指定逻辑密钥生成活动版本材料。
     *
     * @param tenantId  逻辑密钥所属租户标识
     * @param keyRef    逻辑密钥稳定标识
     * @param version   待生成的版本号
     * @param algorithm 密码算法
     * @return 仅供 KMS 可信边界使用的活动密钥版本
     */
    @Override
    public KmsKeyVersion generate(String tenantId, String keyRef, int version, KmsAlgorithm algorithm) {
        try {
            if (algorithm == KmsAlgorithm.ES256) {
                return generateEs256(tenantId, keyRef, version, algorithm, secureRandom);
            }
            if (algorithm == KmsAlgorithm.AES_256_GCM) {
                return generateAes256Gcm(tenantId, keyRef, version, algorithm, secureRandom);
            }
            throw new KmsCryptoException();
        } catch (KmsCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsCryptoException();
        }
    }
}
