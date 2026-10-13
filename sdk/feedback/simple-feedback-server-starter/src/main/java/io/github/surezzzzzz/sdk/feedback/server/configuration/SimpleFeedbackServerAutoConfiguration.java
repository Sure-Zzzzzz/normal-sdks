package io.github.surezzzzzz.sdk.feedback.server.configuration;

import io.github.surezzzzzz.sdk.feedback.server.SimpleFeedbackServerPackage;
import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.support.FallbackAccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.support.FallbackSubmitterProfileProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Feedback Server Auto Configuration。
 *
 * <p>身份门槛说明（DESIGN §8 启动失败条件）：不再使用类级 @ConditionalOnBean——其在
 * auto-configuration 求值时序上对宿主桥可见性不可靠；「身份协作件与宿主桥双缺席=拒启」
 * 由控制器对 {@code FeedbackPrincipalResolver} 的必需构造注入天然保证（Bean 缺失即上下文
 * 启动失败，无匿名回退）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleFeedbackServerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleFeedbackServerPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ANNOTATION,
                value = SimpleFeedbackServerComponent.class)
)
@ConditionalOnProperty(prefix = SimpleFeedbackServerConstant.CONFIG_PREFIX, name = "enable",
        havingValue = "true")
public class SimpleFeedbackServerAutoConfiguration {

    /**
     * SPI 降级实现：可访问应用集（散形态恒返回 null=不过滤；adaptor 覆盖，修正⑩）。
     *
     * <p>让位次序不变量（非直观约束，ticket 同构线同款）：两个自动配置间无显式排序注解，
     * 依赖 spring.factories 字母序 SimpleFeedback<b>IamAdaptor</b> 先于 SimpleFeedback<b>Server</b>
     * 求值——adaptor 扫描件先注册，本降级 @ConditionalOnMissingBean 才能让位；任一侧自动配置
     * 改名即破（届时双 Bean 由协作共存测试拦下，见 walkthrough 包）。</p>
     *
     * @return 降级实现
     */
    @Bean
    @ConditionalOnMissingBean(AccessibleApplicationProvider.class)
    public AccessibleApplicationProvider fallbackAccessibleApplicationProvider() {
        return new FallbackAccessibleApplicationProvider();
    }

    /**
     * SPI 降级实现：提交人联系提示（散形态恒返回 null=空输入框；adaptor 覆盖，修正⑩）。
     *
     * @return 降级实现
     */
    @Bean
    @ConditionalOnMissingBean(SubmitterProfileProvider.class)
    public SubmitterProfileProvider fallbackSubmitterProfileProvider() {
        return new FallbackSubmitterProfileProvider();
    }

    /**
     * 协作装配完整性检查（走查纠错：启动期响亮失败，不静默恒 401）。
     *
     * <p>检测组合缺件：classpath 已引入 iam-resource（验证客户端属性类在场）但公共
     * resource-server 装配件缺席（ResourceAuthenticationFilter 不在场）——该组合下安全链
     * 不会挂认证过滤器，所有受保护请求恒 401。此前仅记 WARN 放行启动，与设计"双缺席拒启、
     * 无匿名回退"矛盾，现改为启动期抛出。宿主自备桥的散形态不受影响（不引 iam-resource 即不触发）。</p>
     *
     * @return 装配完整性检查（InitializingBean 时机执行，纯检查无副作用）
     */
    @Bean
    public org.springframework.beans.factory.InitializingBean collaborationAssemblyCheck() {
        return () -> {
            boolean iamProviderPresent = org.springframework.util.ClassUtils.isPresent(
                    "io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties",
                    SimpleFeedbackServerAutoConfiguration.class.getClassLoader());
            boolean resourceFilterPresent = org.springframework.util.ClassUtils.isPresent(
                    "io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter",
                    SimpleFeedbackServerAutoConfiguration.class.getClassLoader());
            if (iamProviderPresent && !resourceFilterPresent) {
                throw new IllegalStateException("反馈协作装配不完整：classpath 含 simple-iam-resource-server-starter "
                        + "但缺少 simple-resource-server-starter（公共资源层装配件缺席将使全部请求恒 401）。"
                        + "请成对引入协作件，或两者都不引并自备 FeedbackPrincipalResolver（散形态）");
            }
        };
    }
}
