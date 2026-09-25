package io.github.surezzzzzz.sdk.b2m.sms.support;

import io.github.surezzzzzz.sdk.b2m.sms.constant.ErrorCode;
import io.github.surezzzzzz.sdk.b2m.sms.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.b2m.sms.constant.SmsConstant;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsConfigurationException;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * B2M 短信 AES 加解密：失败抛 {@code SmsException}（不返回 null 不吞异常）；
 * PKCS7Padding 需要 BouncyCastle，缺失时显式配置异常。
 *
 * @author surezzzzzz
 */
public final class SmsCryptoHelper {

    private SmsCryptoHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 加密请求体。
     *
     * @param content   明文
     * @param password  密钥字节
     * @param algorithm 算法（平台协议参数）
     * @return 密文
     */
    public static byte[] encrypt(byte[] content, byte[] password, String algorithm) {
        return doCipher(Cipher.ENCRYPT_MODE, content, password, algorithm, ErrorCode.CRYPTO_ENCRYPT_FAILED,
                ErrorMessage.CRYPTO_ENCRYPT_FAILED);
    }

    /**
     * 解密响应体。
     *
     * @param content   密文
     * @param password  密钥字节
     * @param algorithm 算法
     * @return 明文
     */
    public static byte[] decrypt(byte[] content, byte[] password, String algorithm) {
        return doCipher(Cipher.DECRYPT_MODE, content, password, algorithm, ErrorCode.CRYPTO_DECRYPT_FAILED,
                ErrorMessage.CRYPTO_DECRYPT_FAILED);
    }

    /**
     * 校验 AES 密钥长度合法（16/24/32 字节）。
     *
     * @param secretKey 密钥字符串
     * @return 密钥字节
     */
    public static byte[] requireValidKey(String secretKey, String encode) {
        byte[] bytes;
        try {
            bytes = secretKey.getBytes(encode);
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new SmsConfigurationException(ErrorCode.CONFIG_KEY_LENGTH_INVALID,
                    String.format(ErrorMessage.CONFIG_KEY_LENGTH_INVALID, -1), exception);
        }
        boolean valid = false;
        for (int length : SmsConstant.AES_KEY_LENGTHS) {
            if (bytes.length == length) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new SmsConfigurationException(ErrorCode.CONFIG_KEY_LENGTH_INVALID,
                    String.format(ErrorMessage.CONFIG_KEY_LENGTH_INVALID, bytes.length));
        }
        return bytes;
    }

    private static byte[] doCipher(int mode, byte[] content, byte[] password, String algorithm,
                                   String errorCode, String errorMessage) {
        if (content == null || password == null) {
            throw new SmsException(errorCode, errorMessage);
        }
        try {
            Cipher cipher;
            if (algorithm.endsWith(SmsConstant.ALGORITHM_PKCS7_SUFFIX)) {
                try {
                    cipher = Cipher.getInstance(algorithm, SmsConstant.JCE_PROVIDER_BC);
                } catch (GeneralSecurityException providerException) {
                    throw new SmsConfigurationException(ErrorCode.CRYPTO_PROVIDER_MISSING,
                            String.format(ErrorMessage.CRYPTO_PROVIDER_MISSING, algorithm), providerException);
                }
            } else {
                cipher = Cipher.getInstance(algorithm);
            }
            cipher.init(mode, new SecretKeySpec(password, SmsConstant.KEY_ALGORITHM));
            return cipher.doFinal(content);
        } catch (SmsConfigurationException propagate) {
            throw propagate;
        } catch (GeneralSecurityException exception) {
            throw new SmsException(errorCode, errorMessage, exception);
        }
    }
}
