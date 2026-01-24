package io.github.surezzzzzz.sdk.auth.aksk.client.core.executor;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.model.OAuth2TokenResponse;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Token Refresh Executor
 * <p>
 * Token 刷新执行器,封装 Token 状态检查、获取、异步刷新等通用逻辑
 * <p>
 * 设计原则:
 * <ul>
 *   <li>不作为 Spring Bean,由 TokenManager 在构造函数中 new 出来</li>
 *   <li>提供通用的 Token 刷新逻辑,由 RedisTokenManager 和 HttpSessionTokenManager 复用</li>
 *   <li>使用 TaskRetryExecutor 进行重试</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
public class TokenRefreshExecutor {

    /**
     * Token 状态枚举
     */
    public enum TokenStatus {
        /**
         * 已过期
         */
        EXPIRED,
        /**
         * 即将过期
         */
        EXPIRING_SOON,
        /**
         * 正常
         */
        VALID,
        /**
         * 无法解析
         */
        UNPARSABLE
    }

    private final SimpleAkskClientCoreProperties coreProperties;
    private final TaskRetryExecutor retryExecutor;

    /**
     * 单例 RestTemplate（懒加载）
     */
    private static volatile RestTemplate restTemplate;

    /**
     * 构造函数
     *
     * @param coreProperties 配置属性
     * @param retryExecutor  重试执行器
     */
    public TokenRefreshExecutor(
            SimpleAkskClientCoreProperties coreProperties,
            TaskRetryExecutor retryExecutor
    ) {
        this.coreProperties = coreProperties;
        this.retryExecutor = retryExecutor;
    }

    /**
     * 获取 RestTemplate 单例
     */
    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (TokenRefreshExecutor.class) {
                if (restTemplate == null) {
                    // 配置 HTTP 请求工厂
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    // 连接超时：5 秒
                    factory.setConnectTimeout(5000);
                    // 读取超时：10 秒
                    factory.setReadTimeout(15000);

                    restTemplate = new RestTemplate(factory);
                }
            }
        }
        return restTemplate;
    }

    /**
     * 检查 Token 状态
     *
     * @param token JWT Token
     * @return Token 状态
     */
    public TokenStatus checkTokenStatus(String token) {
        try {
            JWT jwt = JWTParser.parse(token);
            Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();

            if (expirationTime == null) {
                log.warn("Token has no expiration time, treating as unparsable");
                return TokenStatus.UNPARSABLE;
            }

            long now = System.currentTimeMillis();
            long expireTime = expirationTime.getTime();
            long refreshBeforeExpire = coreProperties.getToken().getRefreshBeforeExpire() * 1000L;

            if (expireTime <= now) {
                return TokenStatus.EXPIRED;  // 已过期
            } else if ((expireTime - now) <= refreshBeforeExpire) {
                return TokenStatus.EXPIRING_SOON;  // 即将过期
            } else {
                return TokenStatus.VALID;  // 正常
            }
        } catch (Exception e) {
            log.warn("Failed to parse JWT token, treating as unparsable", e);
            return TokenStatus.UNPARSABLE;
        }
    }

    /**
     * 从 OAuth2 Server 获取 Token（带重试）
     *
     * @param securityContext 安全上下文
     * @param cacheCallback   缓存回调函数（参数: accessToken, expiresIn）
     * @return Access Token
     */
    public String fetchTokenFromServer(String securityContext, BiConsumer<String, Long> cacheCallback) {
        try {
            // 使用 TaskRetryExecutor 进行重试：3 次，1 秒间隔，1.5 倍退避，最大 5 秒
            return retryExecutor.executeWithRetry(() -> {
                String tokenUrl = coreProperties.getServerUrl() + coreProperties.getTokenEndpoint();

                // 构建请求
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setBasicAuth(coreProperties.getClientId(), coreProperties.getClientSecret());

                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add(SimpleAkskClientCoreConstant.PARAM_GRANT_TYPE, SimpleAkskClientCoreConstant.GRANT_TYPE_CLIENT_CREDENTIALS);
                if (StringUtils.hasText(securityContext)) {
                    body.add(JwtClaimConstant.SECURITY_CONTEXT, securityContext);
                }

                HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

                ResponseEntity<OAuth2TokenResponse> response = getRestTemplate().exchange(
                        tokenUrl,
                        HttpMethod.POST,
                        request,
                        OAuth2TokenResponse.class
                );

                OAuth2TokenResponse tokenResponse = response.getBody();
                if (tokenResponse == null || !StringUtils.hasText(tokenResponse.getAccessToken())) {
                    throw new TokenFetchException(
                            ClientErrorCode.HTTP_RESPONSE_INVALID,
                            String.format(ClientErrorMessage.HTTP_RESPONSE_INVALID, "Token response is empty")
                    );
                }

                String accessToken = tokenResponse.getAccessToken();
                long expiresIn = tokenResponse.getExpiresIn();

                // 调用缓存回调
                if (cacheCallback != null) {
                    cacheCallback.accept(accessToken, expiresIn);
                }

                log.info("Token fetched from server: expiresIn={}s", expiresIn);

                return accessToken;
            }, 3, 1, 1.5, 5L);
        } catch (Exception e) {
            throw new TokenFetchException(
                    ClientErrorCode.TOKEN_FETCH_FAILED,
                    String.format(ClientErrorMessage.TOKEN_FETCH_FAILED, e.getMessage()),
                    e
            );
        }
    }

    /**
     * 异步刷新 Token
     *
     * @param refreshTask 刷新任务（返回刷新后的 Token）
     */
    public void asyncRefreshToken(Runnable refreshTask) {
        CompletableFuture.runAsync(() -> {
            try {
                refreshTask.run();
            } catch (Exception e) {
                log.error("Failed to refresh token asynchronously", e);
            }
        });
    }
}
