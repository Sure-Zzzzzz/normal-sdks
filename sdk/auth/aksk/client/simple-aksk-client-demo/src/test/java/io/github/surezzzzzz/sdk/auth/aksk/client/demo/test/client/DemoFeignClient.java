package io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.client;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Demo Feign Clients
 *
 * <p>两个客户端定义：
 * <ul>
 *   <li>{@link AkskDemoFeignClient} - 使用 @AkskClientFeignClient，自动携带 AKSK token</li>
 *   <li>{@link PlainDemoFeignClient} - 使用普通 @FeignClient，不携带 AKSK token</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public class DemoFeignClient {

    /**
     * 使用 @AkskClientFeignClient，自动携带 AKSK token（推荐）
     */
    @AkskClientFeignClient(name = "aksk-demo-service", url = "${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    public interface AkskDemoFeignClient {

        @GetMapping("/api/token/statistics")
        String getTokenStatistics();
    }

    /**
     * 使用普通 @FeignClient，不携带 AKSK token
     * 用于验证 1.0.1 修复：去掉 @Configuration 后不再污染全局 Feign 上下文
     */
    @FeignClient(name = "plain-demo-service", url = "${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    public interface PlainDemoFeignClient {

        @GetMapping("/admin/login")
        String loginPage();
    }
}
