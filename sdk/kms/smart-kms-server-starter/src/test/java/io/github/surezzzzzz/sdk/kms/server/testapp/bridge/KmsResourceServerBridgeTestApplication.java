package io.github.surezzzzzz.sdk.kms.server.testapp.bridge;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * 组合式 Resource Server 认证桥 HTTP 测试应用。
 *
 * <p>不注册任何自带 {@code KmsPrincipalResolver}，验证桥在"无宿主 resolver、仅有公共层"
 * 场景下的装配与 HTTP 全链。位于 {@code testapp} 包（不在 {@code test} 包内），避免被
 * 既有测试应用组件扫描导入。排除 AKSK 资源服务自动配置：本应用不经真实公共层
 * FilterChain，认证态由 spring-security-test 直接注入。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, SimpleAkskResourceServerAutoConfiguration.class})
public class KmsResourceServerBridgeTestApplication {
}
