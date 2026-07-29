package io.github.surezzzzzz.sdk.kms.client.port;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;

import java.util.List;

/**
 * 基于完整 KMS Client 的默认租户公钥读取端口。
 *
 * @author surezzzzzz
 */
public class DefaultTenantPublicKeyPort implements TenantPublicKeyPort {

    private final KmsClient kmsClient;

    /**
     * 创建默认租户公钥读取端口。
     *
     * @param kmsClient 完整 KMS Client
     */
    public DefaultTenantPublicKeyPort(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @Override
    public KmsPublicKey read(String keyRef, Integer version) {
        return kmsClient.readPublicKey(keyRef, version);
    }

    @Override
    public List<KmsPublicKey> list(String keyRef) {
        return kmsClient.listPublicKeys(keyRef);
    }
}
