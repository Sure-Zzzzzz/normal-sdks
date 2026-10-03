package io.github.surezzzzzz.sdk.kms.server.testapp.collaboration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KMS 组合式认证协作应用（协作验收三终端中的 KMS 终端）。
 *
 * <p>组合 {@code simple-resource-server-starter} + {@code simple-aksk-resource-server-starter}
 * + 桥装配的 KMS 完整形态：不注册任何自带 resolver，AKSK Bearer 经公共层内省认证后由桥
 * 翻译为 KMS 主体。受保护路径与端口由 {@code collaborationKmsApp} 任务参数注入，
 * AKSK 内省配置与数据库凭据经环境变量注入。位于 {@code testapp} 包（不在 {@code test}
 * 包内），避免被既有测试应用组件扫描导入。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class KmsResourceServerCollaborationApplication {

    /**
     * 启动 KMS 协作应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(KmsResourceServerCollaborationApplication.class, args);
    }
}
