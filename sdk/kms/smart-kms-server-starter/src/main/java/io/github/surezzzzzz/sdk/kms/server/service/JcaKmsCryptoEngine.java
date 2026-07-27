package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsEnvelope;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsCryptoEngine;
import io.github.surezzzzzz.sdk.kms.core.support.KmsEnvelopeHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsEs256SignatureHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * 仅在 KMS 可信边界内使用 JCA 执行密码学操作。
 *
 * @author surezzzzzz
 */
public class JcaKmsCryptoEngine implements KmsCryptoEngine, KmsEnvelopeEncryptionEngine {

    /**
     * 用于生成不可预测 GCM 初始化向量的安全随机源。
     */
    private final SecureRandom secureRandom;

    /**
     * 创建 JCA 密码学适配器。
     *
     * @param secureRandom 由自动配置提供的安全随机源
     */
    public JcaKmsCryptoEngine(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new KmsCryptoException();
        }
        this.secureRandom = secureRandom;
    }

    /**
     * 读取 PKCS#8 EC 私钥材料。
     */
    private static PrivateKey readPrivateKey(byte[] privateMaterial) throws Exception {
        return KeyFactory.getInstance(SmartKmsServerConstant.JCA_EC_KEY_FACTORY_ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(requireBytes(privateMaterial)));
    }

    /**
     * 读取 X.509 SPKI EC 公钥材料。
     */
    private static PublicKey readPublicKey(byte[] publicMaterial) throws Exception {
        return KeyFactory.getInstance(SmartKmsServerConstant.JCA_EC_KEY_FACTORY_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(requireBytes(publicMaterial)));
    }

    /**
     * 读取严格 32 字节的 AES-256 对称材料。
     */
    private static SecretKey readAesKey(byte[] symmetricMaterial) {
        if (symmetricMaterial == null || symmetricMaterial.length != SmartKmsCoreConstant.AES_256_KEY_LENGTH) {
            throw new KmsCryptoException();
        }
        return new SecretKeySpec(symmetricMaterial, SmartKmsServerConstant.JCA_AES_KEY_ALGORITHM);
    }

    /**
     * 创建固定 128 位标签与 12 字节 IV 的 GCM 参数。
     */
    private static GCMParameterSpec gcmParameter(byte[] iv) {
        if (iv == null || iv.length != SmartKmsCoreConstant.GCM_IV_LENGTH) {
            throw new KmsCryptoException();
        }
        return new GCMParameterSpec(SmartKmsServerConstant.JCA_GCM_TAG_SIZE_BITS, iv);
    }

    /**
     * 校验操作算法不能被调用方替换。
     */
    private static void requireAlgorithm(KmsAlgorithm actual, KmsAlgorithm expected) {
        if (actual != expected) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 校验密码学输入非空。
     */
    private static byte[] requireBytes(byte[] value) {
        if (value == null) {
            throw new KmsCryptoException();
        }
        return value;
    }

    /**
     * 使用 ES256 私钥材料生成 low-S JOSE 签名。
     *
     * @param algorithm       固定 ES256 算法
     * @param privateMaterial PKCS#8 私钥材料
     * @param input           待签名输入
     * @return 固定 64 字节 JOSE 签名
     */
    @Override
    public byte[] sign(KmsAlgorithm algorithm, byte[] privateMaterial, byte[] input) {
        requireAlgorithm(algorithm, KmsAlgorithm.ES256);
        try {
            Signature signature = Signature.getInstance(SmartKmsServerConstant.JCA_ES256_SIGNATURE_ALGORITHM);
            signature.initSign(readPrivateKey(privateMaterial), secureRandom);
            signature.update(requireBytes(input));
            return KmsEs256SignatureHelper.derToJose(signature.sign());
        } catch (KmsCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 使用 ES256 公钥材料验证 JOSE 签名。
     *
     * @param algorithm      固定 ES256 算法
     * @param publicMaterial X.509 SPKI 公钥材料
     * @param input          原始签名输入
     * @param signature      JOSE 签名
     * @return 验签通过时返回 {@code true}
     */
    @Override
    public boolean verify(KmsAlgorithm algorithm, byte[] publicMaterial, byte[] input, byte[] signature) {
        requireAlgorithm(algorithm, KmsAlgorithm.ES256);
        try {
            Signature verifier = Signature.getInstance(SmartKmsServerConstant.JCA_ES256_SIGNATURE_ALGORITHM);
            verifier.initVerify(readPublicKey(publicMaterial));
            verifier.update(requireBytes(input));
            return verifier.verify(KmsEs256SignatureHelper.joseToDer(signature));
        } catch (KmsCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsCryptoException();
        }
    }

    /**
     * 使用 AES-256-GCM 加密，输出随机 IV 与密文标签拼接值。
     *
     * @param algorithm         固定 AES_256_GCM 算法
     * @param symmetricMaterial 固定 32 字节对称材料
     * @param plaintext         待加密明文
     * @param aad               已由 Core 封装格式构造的认证附加数据
     * @return 前 12 字节为随机 IV，剩余为密文和 16 字节标签
     */
    @Override
    public byte[] encrypt(KmsAlgorithm algorithm, byte[] symmetricMaterial, byte[] plaintext, byte[] aad) {
        requireAlgorithm(algorithm, KmsAlgorithm.AES_256_GCM);
        byte[] iv = new byte[SmartKmsCoreConstant.GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(SmartKmsServerConstant.JCA_AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, readAesKey(symmetricMaterial), gcmParameter(iv));
            cipher.updateAAD(requireBytes(aad));
            byte[] ciphertextAndTag = cipher.doFinal(requireBytes(plaintext));
            byte[] result = new byte[iv.length + ciphertextAndTag.length];
            System.arraycopy(iv, SmartKmsCoreConstant.ZERO, result, SmartKmsCoreConstant.ZERO, iv.length);
            System.arraycopy(ciphertextAndTag, SmartKmsCoreConstant.ZERO, result, iv.length, ciphertextAndTag.length);
            return result;
        } catch (KmsCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsCryptoException();
        } finally {
            Arrays.fill(iv, (byte) SmartKmsCoreConstant.ZERO);
        }
    }

    /**
     * 生成随机 IV，并使用最终 SKMS 头构造 AAD 后完成 AES-GCM 加密。
     *
     * @param symmetricMaterial 固定 32 字节对称材料
     * @param keyRef            逻辑密钥标识
     * @param keyVersion        精确密钥版本
     * @param plaintext         待加密明文
     * @param externalAad       调用方附加 AAD
     * @return 完整 SKMS v1 二进制封装
     */
    @Override
    public byte[] encryptEnvelope(byte[] symmetricMaterial, String keyRef, int keyVersion, byte[] plaintext,
                                  byte[] externalAad) {
        if (keyVersion < 1) {
            throw new KmsCryptoException();
        }
        byte[] iv = new byte[SmartKmsCoreConstant.GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        byte[] tagPlaceholder = new byte[SmartKmsCoreConstant.GCM_TAG_LENGTH];
        try {
            byte[] header = KmsEnvelopeHelper.serialize(new KmsEnvelope(keyRef, keyVersion, iv, tagPlaceholder));
            byte[] aad = KmsEnvelopeHelper.buildAad(header, externalAad);
            Cipher cipher = Cipher.getInstance(SmartKmsServerConstant.JCA_AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, readAesKey(symmetricMaterial), gcmParameter(iv));
            cipher.updateAAD(aad);
            byte[] ciphertextAndTag = cipher.doFinal(requireBytes(plaintext));
            return KmsEnvelopeHelper.serialize(new KmsEnvelope(keyRef, keyVersion, iv, ciphertextAndTag));
        } catch (KmsCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsCryptoException();
        } finally {
            Arrays.fill(iv, (byte) SmartKmsCoreConstant.ZERO);
            Arrays.fill(tagPlaceholder, (byte) SmartKmsCoreConstant.ZERO);
        }
    }

    /**
     * 使用 AES-256-GCM 解密随机 IV 与密文标签拼接值。
     *
     * @param algorithm         固定 AES_256_GCM 算法
     * @param symmetricMaterial 固定 32 字节对称材料
     * @param ciphertext        前 12 字节为随机 IV，剩余为密文和标签
     * @param aad               已由 Core 封装格式构造的认证附加数据
     * @return 解密后的明文
     */
    @Override
    public byte[] decrypt(KmsAlgorithm algorithm, byte[] symmetricMaterial, byte[] ciphertext, byte[] aad) {
        requireAlgorithm(algorithm, KmsAlgorithm.AES_256_GCM);
        if (ciphertext == null || ciphertext.length < SmartKmsCoreConstant.GCM_IV_LENGTH
                + SmartKmsCoreConstant.GCM_TAG_LENGTH) {
            throw new KmsCryptoException();
        }
        byte[] iv = Arrays.copyOfRange(ciphertext, SmartKmsCoreConstant.ZERO, SmartKmsCoreConstant.GCM_IV_LENGTH);
        byte[] ciphertextAndTag = Arrays.copyOfRange(ciphertext, SmartKmsCoreConstant.GCM_IV_LENGTH, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance(SmartKmsServerConstant.JCA_AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, readAesKey(symmetricMaterial), gcmParameter(iv));
            cipher.updateAAD(requireBytes(aad));
            return cipher.doFinal(ciphertextAndTag);
        } catch (KmsCryptoException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsCryptoException();
        } finally {
            Arrays.fill(iv, (byte) SmartKmsCoreConstant.ZERO);
            Arrays.fill(ciphertextAndTag, (byte) SmartKmsCoreConstant.ZERO);
        }
    }
}
