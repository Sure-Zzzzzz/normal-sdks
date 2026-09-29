package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.SimpleIamAkskCollaborationPackage;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.annotation.SimpleIamAkskCollaborationComponent;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple IAM AKSK Collaboration 自动配置。
 *
 * <p>引依赖即装配，仅扫描本模块自定义注解标记的组件；enable 由 AKSK 侧与适配器侧双开关在
 * 运行时共同判定，未启用时 provider 在场但全部读取返回失败关闭结果。实现替换即替换依赖，
 * 不提供两个 provider 并存的让位语义。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureBefore(name = "io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerAutoConfiguration")
@EnableConfigurationProperties(SimpleIamAkskCollaborationProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamAkskCollaborationPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamAkskCollaborationComponent.class),
        useDefaultFilters = false
)
public class SimpleIamAkskCollaborationAutoConfiguration {
}
