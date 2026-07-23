package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;

/**
 * KMS 密钥材料规则工具。
 *
 * <p>算法和材料必须一一对应：ES256 只允许私钥与公钥，AES-256-GCM 只允许恰好 32 字节
 * 的对称材料。已销毁版本是唯一允许材料全部置空的状态，且必须记录销毁时间。</p>
 *
 * @author surezzzzzz
 */
public final class KmsKeyMaterialHelper {

    private KmsKeyMaterialHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 校验密钥版本的状态、材料与算法一致。
     *
     * @param keyVersion 待校验的内部密钥版本
     * @throws KmsCryptoException 算法未知、状态与销毁时间不一致、必需材料缺失或存在不允许材料时抛出
     */
    public static void validate(KmsKeyVersion keyVersion) {
        if (keyVersion == null || keyVersion.getAlgorithm() == null || keyVersion.getState() == null) {
            throw new KmsCryptoException();
        }
        if (keyVersion.getState() == KmsKeyVersionState.DESTROYED) {
            validateDestroyed(keyVersion);
            return;
        }
        if (keyVersion.getDestroyedAt() != null) {
            throw new KmsCryptoException();
        }
        if (keyVersion.getAlgorithm() == KmsAlgorithm.ES256) {
            validateEs256(keyVersion);
            return;
        }
        if (keyVersion.getAlgorithm() == KmsAlgorithm.AES_256_GCM) {
            validateAes256Gcm(keyVersion);
            return;
        }
        throw new KmsCryptoException();
    }

    /**
     * 校验已销毁版本记录销毁时间且不再保留任何材料。
     */
    private static void validateDestroyed(KmsKeyVersion keyVersion) {
        if (keyVersion.getDestroyedAt() == null || keyVersion.getPrivateMaterial() != null
                || keyVersion.getSymmetricMaterial() != null || keyVersion.getPublicMaterial() != null) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 校验 ES256 私钥与公钥同时存在，且不混入对称材料。
     */
    private static void validateEs256(KmsKeyVersion keyVersion) {
        if (isEmpty(keyVersion.getPrivateMaterial())
                || isEmpty(keyVersion.getPublicMaterial())
                || keyVersion.getSymmetricMaterial() != null) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 校验 AES-256-GCM 仅持有固定长度对称材料。
     */
    private static void validateAes256Gcm(KmsKeyVersion keyVersion) {
        byte[] symmetricMaterial = keyVersion.getSymmetricMaterial();
        if (symmetricMaterial == null
                || symmetricMaterial.length != SmartKmsCoreConstant.AES_256_KEY_LENGTH
                || keyVersion.getPrivateMaterial() != null
                || keyVersion.getPublicMaterial() != null) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 判断材料是否缺失或为空。
     */
    private static boolean isEmpty(byte[] value) {
        return value == null || value.length == SmartKmsCoreConstant.ZERO;
    }
}
