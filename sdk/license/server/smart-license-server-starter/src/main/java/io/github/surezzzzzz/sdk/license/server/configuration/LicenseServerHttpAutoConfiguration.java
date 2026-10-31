package io.github.surezzzzzz.sdk.license.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.license.server.SimpleLicenseServerPackage;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * License Server HTTP 层装配：主体解析桥依赖公共资源链类型。
 *
 * <p>adaptor 装配规范（对齐 IAM/KMS adaptor 先例）：引用即代表使用——classpath 引入公共资源链
 * （协作形态）即装配全部 {@link SimpleLicenseServerComponent} 标记组件；未引入（散形态）时
 * HTTP 面整体不装配，由宿主自备主体解析等价物。无 enable 开关、无让位语义；标记类经构造注入
 * 装配，缺必需依赖（JPA 仓储、签名服务等）启动即报缺 Bean（响亮失败，不静默）。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@EnableScheduling
@ConditionalOnClass(VerifiedResourceContext.class)
@EnableConfigurationProperties(LicenseServerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleLicenseServerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleLicenseServerComponent.class),
        useDefaultFilters = false
)
public class LicenseServerHttpAutoConfiguration {
}
