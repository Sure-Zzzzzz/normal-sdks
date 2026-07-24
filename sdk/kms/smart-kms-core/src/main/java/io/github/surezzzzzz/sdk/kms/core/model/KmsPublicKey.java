package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;

/**
 * 可分发的公钥版本。
 *
 * <p>仅 ES256 的 {@code ACTIVE}、{@code RETIRED} 版本可在服务层转为该模型；
 * 私钥和对称材料绝不进入该模型。</p>
 *
 * @author surezzzzzz
 */
@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class KmsPublicKey {

    /**
     * 逻辑密钥标识。
     */
    @ToString.Include
    private final String keyRef;
    /**
     * 公钥所属版本号。
     */
    @ToString.Include
    private final int version;
    /**
     * 公钥算法，当前只能是 ES256。
     */
    @ToString.Include
    private final KmsAlgorithm algorithm;
    /**
     * 公钥版本状态。
     */
    @ToString.Include
    private final KmsKeyVersionState state;
    /**
     * X.509 SubjectPublicKeyInfo DER 公钥材料。
     */
    private final byte[] publicMaterial;

    /**
     * 创建可发布公钥模型。
     *
     * @param keyRef         逻辑密钥标识
     * @param version        密钥版本号
     * @param algorithm      公钥算法
     * @param state          密钥版本状态
     * @param publicMaterial X.509 SubjectPublicKeyInfo DER 公钥材料
     */
    public KmsPublicKey(String keyRef, int version, KmsAlgorithm algorithm,
                        KmsKeyVersionState state, byte[] publicMaterial) {
        this.keyRef = keyRef;
        this.version = version;
        this.algorithm = algorithm;
        this.state = state;
        this.publicMaterial = copy(publicMaterial);
    }

    /**
     * 复制公钥字节，避免调用方修改模型内部状态。
     */
    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    /**
     * 获取公钥材料副本。
     *
     * @return X.509 SubjectPublicKeyInfo DER 公钥材料副本；原值为空时返回 {@code null}
     */
    public byte[] getPublicMaterial() {
        return copy(publicMaterial);
    }
}
