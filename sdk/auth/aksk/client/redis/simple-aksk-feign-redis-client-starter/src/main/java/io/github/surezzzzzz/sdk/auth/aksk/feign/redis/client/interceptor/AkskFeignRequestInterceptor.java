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
 * @AkskClientFeignClient(name = "my-service", url = "http://localhost:8280")
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
 *     url = "http://localhost:8280",
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
        // debug 埋点：入口记录方法与目标地址，用于排查请求是否被本拦截器处理
        log.debug("[AKSK-Feign] 拦截请求: {} {}", template.method(), template.url());

        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            log.warn("[AKSK-Feign] TokenManager 未返回可用 Token，本次请求不带 Authorization 头: {}", template.url());
        } else {
            // 覆盖 Authorization 头（先移除旧值再写入，避免重复）
            boolean overwritten = template.headers().containsKey(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
            String authorizationValue = String.format(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION_TEMPLATE, token);
            template.removeHeader(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION);
            template.header(SimpleAkskClientCoreConstant.HEADER_AUTHORIZATION, authorizationValue);
            // debug 埋点：不记录 Token 值，只记录长度与是否覆盖调用方已有凭证头
            log.debug("[AKSK-Feign] 已添加 Authorization 头（token 长度={}, 覆盖已有头={}）: {}",
                    token.length(), overwritten, template.url());
        }
    }
}
