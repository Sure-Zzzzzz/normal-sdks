package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import lombok.Builder;
import lombok.Getter;

/**
 * 逻辑密钥元数据。
 *
 * <p>逻辑密钥不承载任何密码材料；材料只属于内部 {@link KmsKeyVersion}。同一逻辑密钥的
 * 所有版本必须具有一致的用途和算法。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsKey {

    /**
     * 逻辑密钥所属 tenant。
     */
    private final String tenantId;
    /**
     * 服务端生成的逻辑密钥稳定标识。
     */
    private final String keyRef;
    /**
     * 便于管理识别的非材料别名。
     */
    private final String keyAlias;
    /**
     * 逻辑密钥的业务用途。
     */
    private final KmsKeyPurpose purpose;
    /**
     * 逻辑密钥及其所有版本使用的密码算法。
     */
    private final KmsAlgorithm algorithm;
    /**
     * 当前逻辑密钥状态。
     */
    private final KmsKeyState state;
    /**
     * 进入待销毁状态前持久化的原状态，仅用于取消销毁时精确恢复。
     */
    private final KmsKeyState stateBeforeDestruction;
    /**
     * 当前活动版本号；没有活动版本时为 {@code null}。
     */
    private final Integer activeVersion;
    /**
     * 持久化层乐观锁版本。
     */
    private final long rowVersion;
}
