package io.github.surezzzzzz.sdk.kms.client.port;

import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.model.KmsPublicKey;

import java.util.List;

/**
 * 基于完整 KMS Client 的默认owner公钥读取端口。
 *
 * @author surezzzzzz
 */
public class DefaultOwnerPublicKeyPort implements OwnerPublicKeyPort {

    private final KmsClient kmsClient;

    /**
     * 创建默认owner公钥读取端口。
     *
     * @param kmsClient 完整 KMS Client
     */
    public DefaultOwnerPublicKeyPort(KmsClient kmsClient) {
        if (kmsClient == null) {
            throw new IllegalArgumentException("kmsClient 不能为空");
        }
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
