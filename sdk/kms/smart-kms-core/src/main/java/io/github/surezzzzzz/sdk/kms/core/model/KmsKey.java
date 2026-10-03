package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Builder;
import lombok.Getter;

/**
 * 逻辑密钥元数据。
 *
 * <p>逻辑密钥不承载密码材料；材料只属于内部 {@link KmsKeyVersion}。ownerPrincipalId 是 KMS
 * 自己持久化的 namespaced 主体标识，不是 IAM 用户记录或展示字段。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class KmsKey {

    private final String ownerPrincipalId;
    private final String keyRef;
    private final String keyAlias;
    private final KmsKeyPurpose purpose;
    private final KmsAlgorithm algorithm;
    private final KmsKeyState state;
    private final KmsKeyState stateBeforeDestruction;
    private final Integer activeVersion;
    private final long rowVersion;

    /**
     * 校验由 builder 创建的不可变密钥快照。
     */
    public KmsKey(String ownerPrincipalId, String keyRef, String keyAlias, KmsKeyPurpose purpose,
                  KmsAlgorithm algorithm, KmsKeyState state, KmsKeyState stateBeforeDestruction,
                  Integer activeVersion, long rowVersion) {
        this.ownerPrincipalId = KmsValidationHelper.requirePrincipalId(ownerPrincipalId);
        this.keyRef = KmsValidationHelper.requireKeyRef(keyRef);
        this.keyAlias = KmsValidationHelper.requireKeyAlias(keyAlias);
        if (purpose == null || algorithm == null || state == null || rowVersion < 0L) {
            throw new io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException();
        }
        this.purpose = purpose;
        this.algorithm = algorithm;
        this.state = state;
        this.stateBeforeDestruction = stateBeforeDestruction;
        this.activeVersion = activeVersion;
        this.rowVersion = rowVersion;
    }
}
