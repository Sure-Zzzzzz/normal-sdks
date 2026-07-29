package io.github.surezzzzzz.sdk.kms.client.support;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsClientConfigurationException;

import java.net.URI;

/**
 * KMS Client 地址校验帮助类。
 *
 * <p>将配置限制为纯 HTTP/HTTPS origin，再统一追加固定 API 根路径，阻止路径、查询、片段或用户信息
 * 参与目标地址构造。</p>
 *
 * @author surezzzzzz
 */
public final class KmsClientUriHelper {

    private KmsClientUriHelper() {
    }

    /**
     * 校验 origin 并生成固定 KMS API 根地址。
     *
     * @param baseUrl 仅包含协议、主机和可选端口的 KMS origin
     * @return 固定追加 KMS API 路径后的地址
     * @throws KmsClientConfigurationException 地址不符合 KMS origin 边界时抛出
     */
    public static URI apiBaseUri(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw configuration();
        }
        try {
            URI origin = URI.create(baseUrl);
            if (!isOrigin(origin)) {
                throw configuration();
            }
            return new URI(origin.getScheme(), null, origin.getHost(), origin.getPort(),
                    SimpleKmsClientConstant.API_BASE_PATH, null, null);
        } catch (KmsClientConfigurationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw configuration();
        }
    }

    /**
     * 校验固定 KMS API 根地址。
     *
     * @param apiBaseUri KMS API 根地址
     * @return 已验证的固定 API 根地址
     */
    public static URI fixedApiBaseUri(URI apiBaseUri) {
        if (apiBaseUri == null || !isApiBaseUri(apiBaseUri)) {
            throw configuration();
        }
        return apiBaseUri;
    }

    private static boolean isOrigin(URI origin) {
        return origin.isAbsolute() && origin.getHost() != null
                && ("http".equalsIgnoreCase(origin.getScheme()) || "https".equalsIgnoreCase(origin.getScheme()))
                && origin.getUserInfo() == null && origin.getQuery() == null && origin.getFragment() == null
                && (origin.getPath() == null || origin.getPath().isEmpty() || "/".equals(origin.getPath()));
    }

    private static boolean isApiBaseUri(URI apiBaseUri) {
        return apiBaseUri.isAbsolute() && apiBaseUri.getHost() != null
                && ("http".equalsIgnoreCase(apiBaseUri.getScheme()) || "https".equalsIgnoreCase(apiBaseUri.getScheme()))
                && apiBaseUri.getUserInfo() == null && apiBaseUri.getQuery() == null && apiBaseUri.getFragment() == null
                && SimpleKmsClientConstant.API_BASE_PATH.equals(apiBaseUri.getPath());
    }

    private static KmsClientConfigurationException configuration() {
        return new KmsClientConfigurationException(SimpleKmsClientConstant.MESSAGE_INVALID_CONFIGURATION);
    }
}
