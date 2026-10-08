package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.walkthrough;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 走查宿主：portal 形态（公共资源层 + IAM 人员令牌 + AKSK 机器令牌）接收真实门户流量。
 *
 * <p>仅供 iam.zs.com 走查起服，不进入模块测试；认证走公共 Resource Server 的
 * kid 路由（iam/* → IAM VC 回源，aksk/* → AKSK 内省），凭据经环境变量注入。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SmartRedisLimiterManagementWalkthroughApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRedisLimiterManagementWalkthroughApplication.class, args);
    }
}
