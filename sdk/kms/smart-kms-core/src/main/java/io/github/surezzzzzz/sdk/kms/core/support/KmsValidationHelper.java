package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;

/**
 * KMS 标识输入校验工具。
 *
 * <p>本类只校验通用文本边界：非空、非纯空白、长度上限且不含控制字符。
 * keyRef 等服务端生成的 opaque 标识不在 core 中附加业务格式或字符集规则。</p>
 *
 * @author surezzzzzz
 */
public final class KmsValidationHelper {

    private KmsValidationHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 校验 tenant 标识。
     *
     * @param value tenant 标识
     * @return 原始 tenant 标识
     */
    public static String requireTenantId(String value) {
        return requireText(value, SmartKmsCoreConstant.TENANT_ID_MAX_LENGTH);
    }

    /**
     * 校验认证主体标识。
     *
     * @param value 主体标识
     * @return 原始主体标识
     */
    public static String requirePrincipalId(String value) {
        return requireText(value, SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH);
    }

    /**
     * 校验逻辑密钥标识。
     *
     * @param value 服务端生成的 opaque keyRef
     * @return 原始 keyRef
     */
    public static String requireKeyRef(String value) {
        return requireText(value, SmartKmsCoreConstant.KEY_REF_MAX_LENGTH);
    }

    /**
     * 校验逻辑密钥别名。
     *
     * @param value 密钥别名
     * @return 原始密钥别名
     */
    public static String requireKeyAlias(String value) {
        return requireText(value, SmartKmsCoreConstant.KEY_ALIAS_MAX_LENGTH);
    }

    /**
     * 校验授权策略标识。
     *
     * @param value 策略标识
     * @return 原始策略标识
     */
    public static String requirePolicyId(String value) {
        return requireText(value, SmartKmsCoreConstant.POLICY_ID_MAX_LENGTH);
    }

    /**
     * 校验审计关联请求标识。
     *
     * @param value 请求标识
     * @return 原始请求标识
     */
    public static String requireRequestId(String value) {
        return requireText(value, SmartKmsCoreConstant.REQUEST_ID_MAX_LENGTH);
    }

    /**
     * 校验客户端幂等键。
     *
     * @param value 幂等键
     * @return 原始幂等键
     */
    public static String requireIdempotencyKey(String value) {
        return requireText(value, SmartKmsCoreConstant.IDEMPOTENCY_KEY_MAX_LENGTH);
    }

    /**
     * 校验通用 KMS 文本标识。
     *
     * @param value     待校验文本
     * @param maxLength 允许的最大 UTF-16 字符数
     * @return 原始文本
     * @throws KmsValidationException 文本为 {@code null}、空、纯空白、超长或包含控制字符时抛出
     */
    public static String requireText(String value, int maxLength) {
        if (value == null || value.length() == SmartKmsCoreConstant.ZERO
                || value.length() > maxLength || !containsNonWhitespace(value)) {
            throw new KmsValidationException();
        }
        for (int index = SmartKmsCoreConstant.ZERO; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new KmsValidationException();
            }
        }
        return value;
    }

    /**
     * 判断文本至少包含一个非空白字符。
     */
    private static boolean containsNonWhitespace(String value) {
        for (int index = SmartKmsCoreConstant.ZERO; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)) {
                return true;
            }
        }
        return false;
    }
}
