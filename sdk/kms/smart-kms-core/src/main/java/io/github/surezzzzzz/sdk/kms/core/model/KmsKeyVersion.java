package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Arrays;

/**
 * 密钥版本内部模型。
 *
 * <p>该模型可承载服务进程内的材料，因此只能在 KMS 可信边界内使用，不能作为 HTTP、审计、
 * 日志或领域事件载荷。材料字段不参与 {@code toString()}，且读取时均返回防御性副本。</p>
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class KmsKeyVersion {

    /**
     * 密钥所属租户。
     */
    private final String tenantId;
    /**
     * 逻辑密钥标识。
     */
    @ToString.Include
    private final String keyRef;
    /**
     * 单逻辑密钥内严格递增的版本号。
     */
    @ToString.Include
    private final int version;
    /**
     * 此版本固定使用的算法。
     */
    @ToString.Include
    private final KmsAlgorithm algorithm;
    /**
     * 当前版本状态。
     */
    @ToString.Include
    private final KmsKeyVersionState state;
    /**
     * 安排销毁前的版本状态，仅用于取消销毁时精确恢复。
     */
    private final KmsKeyVersionState stateBeforeDestruction;
    /**
     * ES256 PKCS#8 DER 私钥材料，仅可信边界内部可用。
     */
    private final byte[] privateMaterial;
    /**
     * AES-256-GCM 原始对称材料，仅可信边界内部可用。
     */
    private final byte[] symmetricMaterial;
    /**
     * ES256 X.509 SubjectPublicKeyInfo DER 公钥材料。
     */
    private final byte[] publicMaterial;
    /**
     * 密码材料已置空且版本进入已销毁状态的时间；其他状态必须为 {@code null}。
     */
    private final Instant destroyedAt;

    /**
     * 创建内部密钥版本。
     *
     * <p>算法、状态、销毁时间与材料组合的完整性由 {@code KmsKeyMaterialHelper} 校验，构造器仅建立
     * 不可变快照，以便持久化适配器可表达材料缺失或销毁后的状态。</p>
     *
     * @param tenantId               密钥所属租户
     * @param keyRef                 逻辑密钥标识
     * @param version                密钥版本号
     * @param algorithm              密钥算法
     * @param state                  当前版本状态
     * @param stateBeforeDestruction 安排销毁前的版本状态
     * @param privateMaterial        ES256 私钥材料
     * @param symmetricMaterial      AES-256-GCM 对称材料
     * @param publicMaterial         ES256 公钥材料
     * @param destroyedAt            密码材料已销毁时间；未销毁时为 {@code null}
     */
    public KmsKeyVersion(String tenantId, String keyRef, int version, KmsAlgorithm algorithm,
                         KmsKeyVersionState state, KmsKeyVersionState stateBeforeDestruction,
                         byte[] privateMaterial, byte[] symmetricMaterial, byte[] publicMaterial,
                         Instant destroyedAt) {
        this.tenantId = tenantId;
        this.keyRef = keyRef;
        this.version = version;
        this.algorithm = algorithm;
        this.state = state;
        this.stateBeforeDestruction = stateBeforeDestruction;
        this.privateMaterial = copy(privateMaterial);
        this.symmetricMaterial = copy(symmetricMaterial);
        this.publicMaterial = copy(publicMaterial);
        this.destroyedAt = destroyedAt;
    }

    /**
     * 获取私钥材料副本。
     *
     * @return PKCS#8 DER 私钥材料副本；原值为空时返回 {@code null}
     */
    public byte[] getPrivateMaterial() {
        return copy(privateMaterial);
    }

    /**
     * 获取对称材料副本。
     *
     * @return AES-256-GCM 原始材料副本；原值为空时返回 {@code null}
     */
    public byte[] getSymmetricMaterial() {
        return copy(symmetricMaterial);
    }

    /**
     * 获取公钥材料副本。
     *
     * @return X.509 SubjectPublicKeyInfo DER 公钥材料副本；原值为空时返回 {@code null}
     */
    public byte[] getPublicMaterial() {
        return copy(publicMaterial);
    }

    /**
     * 复制材料字节，避免任何调用方持有内部可变数组。
     */
    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
