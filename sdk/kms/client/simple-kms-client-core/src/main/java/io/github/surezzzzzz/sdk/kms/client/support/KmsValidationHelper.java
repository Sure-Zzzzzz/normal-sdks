package io.github.surezzzzzz.sdk.kms.client.support;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsBadRequestException;

/**
 * KMS Client 参数校验帮助类。
 *
 * <p>所有公开参数边界统一抛出稳定的 KMS Client 异常，避免向调用方泄露通用运行时参数异常。</p>
 *
 * @author surezzzzzz
 */
public final class KmsValidationHelper {

    private KmsValidationHelper() {
    }

    /**
     * 校验非空文本。
     *
     * @param value 待校验文本
     * @return 原始非空文本
     * @throws KmsBadRequestException 文本为空时抛出
     */
    public static String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new KmsBadRequestException(SimpleKmsClientConstant.MESSAGE_INVALID_REQUEST, null, null, null, null, null);
        }
        return value;
    }

    /**
     * 校验非空对象。
     *
     * @param value 待校验对象
     * @param <T>   对象类型
     * @return 原始非空对象
     * @throws KmsBadRequestException 对象为空时抛出
     */
    public static <T> T requireValue(T value) {
        if (value == null) {
            throw new KmsBadRequestException(SimpleKmsClientConstant.MESSAGE_INVALID_REQUEST, null, null, null, null, null);
        }
        return value;
    }
}
