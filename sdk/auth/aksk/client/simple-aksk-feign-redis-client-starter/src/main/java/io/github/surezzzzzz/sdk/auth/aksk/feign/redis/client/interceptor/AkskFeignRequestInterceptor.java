package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AKSK Feign Request Interceptor
 *
 * <p>自动为 Feign 请求添加 Authorization 头。
 *
 * <p>核心功能：
 * <ul>
 *   <li>自动从 TokenManager 获取 Token</li>
 *   <li>自动添加 Authorization 头（Bearer Token）</li>
 * </ul>
 *
 * <p>使用方式1：使用 @AkskClientFeignClient 注解（推荐）
 * <pre>{@code
 * @AkskClientFeignClient(name = "my-service", url = "http://localhost:8080")
 * public interface MyServiceClient {
 *     @GetMapping("/api/resource")
 *     String getResource();
 * }
 * }</pre>
 *
 * <p>使用方式2：显式配置拦截器
 * <pre>{@code
 * @FeignClient(
 *     name = "my-service",
 *     url = "http://localhost:8080",
 *     configuration = AkskFeignConfiguration.class
 * )
 * public interface MyServiceClient {
 *     @GetMapping("/api/resource")
 *     String getResource();
 * }
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class AkskFeignRequestInterceptor implements RequestInterceptor {

    private final TokenManager tokenManager;

    @Override
    public void apply(RequestTemplate template) {
        // 获取 Token
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("No token available, proceeding without Authorization header");
        } else {
            // 添加 Authorization 头
            String authorizationValue = String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token);
            template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION, authorizationValue);
            log.debug("Added Authorization header to request: {}", template.url());
        }
    }
}
