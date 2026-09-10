package io.github.surezzzzzz.sdk.auth.collaboration.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开健康接口，位于 permit-all 路径，不采集 Bearer 凭据。
 *
 * @author surezzzzzz
 */
@RestController
public class PublicHealthController {

    /**
     * 健康状态。
     *
     * @return 固定健康响应
     */
    @GetMapping("/public/health")
    public String health() {
        return "ok";
    }
}
