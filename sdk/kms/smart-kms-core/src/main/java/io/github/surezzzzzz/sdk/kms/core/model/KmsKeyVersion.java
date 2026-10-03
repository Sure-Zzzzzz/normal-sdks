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
 * <p>该模型只能在 KMS 可信边界内使用。材料字段不参与 {@code toString()}，读取时均返回
 * 防御性副本，不能转换为 HTTP、审计或日志载荷。</p>
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class KmsKeyVersion {

    private final String ownerPrincipalId;
    @ToString.Include
    private final String keyRef;
    @ToString.Include
    private final int version;
    @ToString.Include
    private final KmsAlgorithm algorithm;
    @ToString.Include
    private final KmsKeyVersionState state;
    private final KmsKeyVersionState stateBeforeDestruction;
    private final byte[] privateMaterial;
    private final byte[] symmetricMaterial;
    private final byte[] publicMaterial;
    private final Instant destroyedAt;

    public KmsKeyVersion(String ownerPrincipalId, String keyRef, int version, KmsAlgorithm algorithm, KmsKeyVersionState state,
                         KmsKeyVersionState stateBeforeDestruction, byte[] privateMaterial,
                         byte[] symmetricMaterial, byte[] publicMaterial, Instant destroyedAt) {
        this.ownerPrincipalId = ownerPrincipalId;
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

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    public byte[] getPrivateMaterial() {
        return copy(privateMaterial);
    }

    public byte[] getSymmetricMaterial() {
        return copy(symmetricMaterial);
    }

    public byte[] getPublicMaterial() {
        return copy(publicMaterial);
    }
}
