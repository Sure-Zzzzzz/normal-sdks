package io.github.surezzzzzz.sdk.kms.client.port;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSignature;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSigningResult;

/**
 * 基于完整 KMS Client 的默认租户签名端口。
 *
 * <p>将 HTTP 层 {@link KmsSignature} 投影为不暴露 keyRef 的 {@link KmsSigningResult}。</p>
 *
 * @author surezzzzzz
 */
public class DefaultTenantSignerPort implements TenantSignerPort {

    private final KmsClient kmsClient;

    /**
     * 创建默认租户签名端口。
     *
     * @param kmsClient 完整 KMS Client
     */
    public DefaultTenantSignerPort(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @Override
    public KmsSigningResult sign(String keyRef, Integer version, byte[] signingInput) {
        KmsSignature signature = kmsClient.sign(keyRef, version, signingInput);
        return KmsSigningResult.builder().version(signature.getVersion())
                .algorithm(SimpleKmsClientConstant.ALGORITHM_ES256).signature(signature.getSignature()).build();
    }
}
