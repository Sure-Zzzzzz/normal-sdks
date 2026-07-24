package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;

import java.time.Instant;

/**
 * KMS 精确授权工具。
 *
 * <p>策略只支持 allow-only 精确匹配，不支持主体、密钥、操作或版本通配。scope 校验由
 * {@code KmsAuthorizationService} 负责，本类仅判定单条策略是否与已认证主体匹配。</p>
 *
 * @author surezzzzzz
 */
public final class KmsAuthorizationHelper {

    private KmsAuthorizationHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 判断策略是否精确授权当前主体的目标操作。
     *
     * <p>未指定版本表示匹配当前逻辑密钥的任意非销毁版本；到期瞬间及之后均不再匹配。</p>
     *
     * @param policy    待判定策略
     * @param principal 已认证主体，tenant 只能从此对象获得
     * @param keyRef    目标逻辑密钥标识
     * @param version   目标版本号
     * @param operation 目标操作
     * @param now       权威当前时间
     * @return 策略精确匹配且尚未到期时返回 {@code true}
     */
    public static boolean matches(KmsKeyPolicy policy, KmsPrincipal principal, String keyRef,
                                  int version, KmsOperation operation, Instant now) {
        return policy != null
                && principal != null
                && operation != null
                && now != null
                && isPolicyOperation(operation)
                && isPolicyOperation(policy.getOperation())
                && policy.getTenantId().equals(principal.getTenantId())
                && policy.getPrincipalId().equals(principal.getPrincipalId())
                && policy.getKeyRef().equals(keyRef)
                && policy.getOperation() == operation
                && (policy.getKeyVersion() == null || policy.getKeyVersion().intValue() == version)
                && (policy.getExpiresAt() == null || policy.getExpiresAt().isAfter(now));
    }

    private static boolean isPolicyOperation(KmsOperation operation) {
        return operation == KmsOperation.SIGN
                || operation == KmsOperation.VERIFY
                || operation == KmsOperation.ENCRYPT
                || operation == KmsOperation.DECRYPT
                || operation == KmsOperation.READ_PUBLIC_KEY;
    }
}
