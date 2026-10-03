package io.github.surezzzzzz.sdk.kms.client.auth;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.kms.client.client.KmsClientAuthenticationInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 复用 AKSK Token Manager 的 KMS 认证拦截器。
 *
 * <p>面向持有 AKP/AKU 凭据的业务服务：宿主装配任一 {@link TokenManager}（如
 * simple-aksk-resttemplate-httpsession-client-starter）后，本拦截器自动把其令牌写入
 * KMS 认可的 Bearer 头，业务无需自行实现令牌获取与缓存。凭据仍由 AKSK client 配置
 * 提供，本类不读取、存储或生成任何凭据。</p>
 *
 * @author surezzzzzz
 */
public class AkskTokenManagerKmsAuthenticationInterceptor implements KmsClientAuthenticationInterceptor {

    private final TokenManager tokenManager;

    /**
     * 创建拦截器。
     *
     * @param tokenManager 宿主装配的 AKSK 令牌管理器
     */
    public AkskTokenManagerKmsAuthenticationInterceptor(TokenManager tokenManager) {
        if (tokenManager == null) {
            throw new IllegalArgumentException("tokenManager 不能为空");
        }
        this.tokenManager = tokenManager;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String token = tokenManager.getToken();
        if (token != null && !token.isEmpty()) {
            request.getHeaders().setBearerAuth(token);
        }
        return execution.execute(request, body);
    }
}
