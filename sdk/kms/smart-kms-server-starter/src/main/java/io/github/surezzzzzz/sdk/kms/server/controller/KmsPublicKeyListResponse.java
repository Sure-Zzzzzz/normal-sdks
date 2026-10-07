package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPublicKey;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 本人公钥的安全响应容器；私有转换器将 items 写为契约规定的裸数组。
 *
 * @author surezzzzzz
 */
@Getter
public class KmsPublicKeyListResponse {
    /**
     * 只包含公钥文本的版本列表。
     */
    private final List<PublicKeyResponse> items;

    /**
     * 从已经验证的公钥建立安全字段投影。
     *
     * @param publicKeys 可分发公钥
     */
    public KmsPublicKeyListResponse(List<KmsPublicKey> publicKeys) {
        List<PublicKeyResponse> result = new ArrayList<PublicKeyResponse>();
        for (KmsPublicKey source : publicKeys) {
            result.add(new PublicKeyResponse(source.getKeyRef(), source.getVersion(), source.getAlgorithm().getCode(),
                    source.getState().getCode(), Base64.getUrlEncoder().withoutPadding().encodeToString(source.getPublicMaterial())));
        }
        this.items = Collections.unmodifiableList(result);
    }

    /**
     * 单个公钥的 HTTP 安全字段。
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PublicKeyResponse {
        /**
         * 逻辑密钥标识。
         */
        private final String keyRef;
        /**
         * 密钥版本。
         */
        private final int version;
        /**
         * 算法编码。
         */
        private final String algorithm;
        /**
         * 版本状态编码。
         */
        private final String state;
        /**
         * 无填充 Base64url 公钥文本。
         */
        private final String publicKey;
    }
}
