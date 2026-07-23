package io.github.surezzzzzz.sdk.kms.core.support;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;

/**
 * KMS 状态规则工具。
 *
 * <p>逻辑密钥状态和版本状态必须同时判定。本类只给出无 I/O 的确定性规则，事务、锁和状态持久化
 * 由 server 层实现。</p>
 *
 * @author surezzzzzz
 */
public final class KmsStateHelper {

    private KmsStateHelper() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 判断给定操作是否允许使用指定逻辑密钥和版本。
     *
     * <p>签名和新加密只允许双 {@code ACTIVE}；历史解密只允许逻辑密钥 {@code ACTIVE} 且版本为
     * {@code ACTIVE}/{@code RETIRED}；验签和读取公钥可在逻辑密钥禁用后继续进行。待销毁和已销毁
     * 状态始终拒绝，避免从发布接口或历史密文恢复路径绕过销毁。</p>
     *
     * @param keyState     逻辑密钥状态
     * @param versionState 密钥版本状态
     * @param operation    待执行操作
     * @return 可以执行时返回 {@code true}
     */
    public static boolean canExecute(KmsKeyState keyState, KmsKeyVersionState versionState,
                                     KmsOperation operation) {
        if (keyState == null || versionState == null || operation == null) {
            return false;
        }
        if (keyState == KmsKeyState.PENDING_DESTRUCTION || keyState == KmsKeyState.DESTROYED) {
            return false;
        }
        if (operation == KmsOperation.SIGN || operation == KmsOperation.ENCRYPT) {
            return keyState == KmsKeyState.ACTIVE && versionState == KmsKeyVersionState.ACTIVE;
        }
        if (operation == KmsOperation.DECRYPT) {
            return keyState == KmsKeyState.ACTIVE
                    && (versionState == KmsKeyVersionState.ACTIVE
                    || versionState == KmsKeyVersionState.RETIRED);
        }
        return (keyState == KmsKeyState.ACTIVE || keyState == KmsKeyState.DISABLED)
                && (versionState == KmsKeyVersionState.ACTIVE
                || versionState == KmsKeyVersionState.RETIRED);
    }

    /**
     * 判断逻辑密钥状态迁移是否合法。
     *
     * @param source 当前逻辑密钥状态
     * @param target 目标逻辑密钥状态
     * @return 迁移受状态机允许时返回 {@code true}
     */
    public static boolean canTransition(KmsKeyState source, KmsKeyState target) {
        if (source == null || target == null) {
            return false;
        }
        return (source == KmsKeyState.ACTIVE
                && (target == KmsKeyState.DISABLED || target == KmsKeyState.PENDING_DESTRUCTION))
                || (source == KmsKeyState.DISABLED
                && (target == KmsKeyState.ACTIVE || target == KmsKeyState.PENDING_DESTRUCTION))
                || (source == KmsKeyState.PENDING_DESTRUCTION
                && (target == KmsKeyState.ACTIVE || target == KmsKeyState.DISABLED
                || target == KmsKeyState.DESTROYED));
    }

    /**
     * 判断密钥版本状态迁移是否合法。
     *
     * <p>版本一旦退役不能重新成为活动版本；重新激活只允许取消尚未执行的销毁，从而避免通过状态回退
     * 破坏单活动版本约束。</p>
     *
     * @param source 当前版本状态
     * @param target 目标版本状态
     * @return 迁移受状态机允许时返回 {@code true}
     */
    public static boolean canTransition(KmsKeyVersionState source, KmsKeyVersionState target) {
        if (source == null || target == null) {
            return false;
        }
        return (source == KmsKeyVersionState.ACTIVE
                && (target == KmsKeyVersionState.RETIRED
                || target == KmsKeyVersionState.PENDING_DESTRUCTION))
                || (source == KmsKeyVersionState.RETIRED
                && target == KmsKeyVersionState.PENDING_DESTRUCTION)
                || (source == KmsKeyVersionState.PENDING_DESTRUCTION
                && (target == KmsKeyVersionState.ACTIVE
                || target == KmsKeyVersionState.RETIRED
                || target == KmsKeyVersionState.DESTROYED));
    }

    /**
     * 判断版本公钥是否可以对外分发。
     *
     * @param keyState     逻辑密钥状态
     * @param versionState 密钥版本状态
     * @return 可分发时返回 {@code true}
     */
    public static boolean isPublishablePublicKey(KmsKeyState keyState,
                                                 KmsKeyVersionState versionState) {
        if (keyState == null || versionState == null) {
            return false;
        }
        return (keyState == KmsKeyState.ACTIVE || keyState == KmsKeyState.DISABLED)
                && (versionState == KmsKeyVersionState.ACTIVE
                || versionState == KmsKeyVersionState.RETIRED);
    }
}
