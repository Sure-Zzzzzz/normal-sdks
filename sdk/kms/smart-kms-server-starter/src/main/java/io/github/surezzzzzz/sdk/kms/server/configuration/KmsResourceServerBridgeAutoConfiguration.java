package io.github.surezzzzzz.sdk.kms.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsResourceServerPrincipalResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.util.Map;

/**
 * 组合式 Resource Server 认证桥自动配置。
 *
 * <p>必须独立于 {@link SmartKmsServerAutoConfiguration} 存在：主配置类级
 * {@code @ConditionalOnBean(KmsPrincipalResolver.class)} 的条件求值先于其自身
 * {@code @Bean} 方法注册，若桥 Bean 放在主配置内部，在"无宿主 resolver、仅有公共层"
 * 这一桥唯一必需的场景下类级条件看不到桥，整个 KMS 自动配置会被跳过。本配置通过
 * {@code @AutoConfigureBefore} 先于主配置求值，使桥 Bean 定义对主配置类级条件可见，
 * 时序才成立。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@AutoConfigureBefore(SmartKmsServerAutoConfiguration.class)
@ConditionalOnClass(VerifiedResourceContext.class)
public class KmsResourceServerBridgeAutoConfiguration {

    private static final String RESOURCE_AUTHENTICATION_FILTER_CLASS =
            "io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter";

    private static void logAssembly(ListableBeanFactory beanFactory) {
        Map<String, KmsPrincipalResolver> resolvers = beanFactory.getBeansOfType(KmsPrincipalResolver.class);
        if (resolvers.isEmpty()) {
            log.debug("KMS 资源层桥装配事实：容器内无 KmsPrincipalResolver，KMS 自动配置未装配");
        } else {
            KmsPrincipalResolver resolver = resolvers.values().iterator().next();
            if (resolver instanceof KmsResourceServerPrincipalResolver) {
                log.info("KMS 资源层认证桥已装配：classpath 检测到 simple-resource-server-core，"
                        + "KmsResourceServerPrincipalResolver 已注册");
            } else {
                log.info("KMS 资源层认证桥让位：检测到宿主自定义 KmsPrincipalResolver（{}），桥未注册",
                        resolver.getClass().getName());
            }
        }
        boolean starterPresent = ClassUtils.isPresent(RESOURCE_AUTHENTICATION_FILTER_CLASS,
                KmsResourceServerBridgeAutoConfiguration.class.getClassLoader());
        if (!starterPresent) {
            log.warn("KMS 资源层配对告警：classpath 仅有 simple-resource-server-core 而未检测到 "
                    + "simple-resource-server-starter，无组件填充已验证上下文，所有 KMS 请求将恒 401；"
                    + "core 应与 starter 成对引入");
        }
    }

    /**
     * 注册组合式 Resource Server 认证桥。
     *
     * <p>使用 {@code @Bean} 而非组件扫描的原因（规范 §4.2）：桥需要"公共层类存在 +
     * 宿主 resolver 恒优先让位 + 先于主配置类级条件求值"三重装配语义，条件求值顺序
     * 是功能正确性的一部分，不适合组件扫描注册。公共层不在 classpath 时本配置整体
     * 不生效，KMS 行为与既有宿主完全一致。</p>
     *
     * @return 组合式 Resource Server 认证桥
     */
    @Bean
    @ConditionalOnMissingBean(KmsPrincipalResolver.class)
    public KmsResourceServerPrincipalResolver kmsResourceServerPrincipalResolver() {
        return new KmsResourceServerPrincipalResolver();
    }

    /**
     * 注册桥装配事实启动日志监听器。
     *
     * <p>使用 {@code @Bean} 的原因：让位分支下桥 {@code @Bean} 方法不执行，装配
     * 结论（桥激活 / 让位含宿主 resolver 类名）必须在容器就绪后读取容器事实统一
     * 输出。监听器只读容器、纯日志输出，不参与任何装配决策。</p>
     *
     * @param beanFactory 应用 Bean 工厂
     * @return 桥装配事实日志监听器
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> kmsResourceServerBridgeAssemblyLogger(
            ListableBeanFactory beanFactory) {
        return event -> logAssembly(beanFactory);
    }
}
