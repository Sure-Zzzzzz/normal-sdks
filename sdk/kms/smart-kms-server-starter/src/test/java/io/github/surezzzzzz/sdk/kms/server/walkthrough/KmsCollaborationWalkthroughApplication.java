package io.github.surezzzzzz.sdk.kms.server.walkthrough;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 走查宿主：完整协作形态（公共资源层 + IAM 人员令牌 + AKSK 令牌）接收真实门户流量。
 *
 * <p>与两个测试应用（均排除真实认证链）不同，本宿主不排除任何自动配置，
 * 认证走公共 Resource Server 的 kid 路由（iam/* → IAM VC 回源，aksk/* → AKSK 内省）。
 * 仅供 iam.zs.com 走查起服，不进入模块测试。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class KmsCollaborationWalkthroughApplication {

    public static void main(String[] args) {
        SpringApplication.run(KmsCollaborationWalkthroughApplication.class, args);
    }

    /**
     * 走查宿主使用本机目录解析主体显示名；SDK 默认解析器因此让位。
     */
    @Bean
    public WalkthroughPrincipalDisplayNameResolver walkthroughPrincipalDisplayNameResolver(
            Environment environment) {
        return new WalkthroughPrincipalDisplayNameResolver(environment);
    }
}
