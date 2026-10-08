package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementConfigurationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterPolicyAuthorization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Portal 装配门禁只校验本地资源链，不在启动时回源探测权限。
 * <p>对公共资源链 starter 独有类型的探测走反射：聚散原则下 starter 是部署期装配件，
 * 只出现在测试与宿主类路径；本模块编译期仅依赖 core 契约，类型与方法名以常量字符串声明，
 * 反射失败即视为门禁不通过。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX,
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_MODE,
        havingValue = SmartRedisLimiterManagementConstant.MODE_PORTAL)
public class SmartRedisLimiterManagementPortalConfiguration {
    private static SmartRedisLimiterManagementConfigurationException resourceRequired() {
        return new SmartRedisLimiterManagementConfigurationException(ErrorCode.CONFIG_VALIDATION_FAILED,
                String.format(ErrorMessage.CONFIG_VALIDATION_FAILED, ErrorMessage.CONFIG_PORTAL_RESOURCE_REQUIRED));
    }

    private static boolean containsProtectedPath(Object security, String path) throws Exception {
        Iterable<?> protectedPaths = (Iterable<?>) security.getClass()
                .getMethod("getProtectedPaths").invoke(security);
        for (Object protectedPath : protectedPaths) {
            if (path.equals(protectedPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean permitAllPathsEmpty(Object security) throws Exception {
        Iterable<?> permitAllPaths = (Iterable<?>) security.getClass()
                .getMethod("getPermitAllPaths").invoke(security);
        return !permitAllPaths.iterator().hasNext();
    }

    /**
     * 创建可替换的来源中立业务授权器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SmartRedisLimiterPolicyAuthorization smartRedisLimiterPolicyAuthorization() {
        return new SmartRedisLimiterPolicyAuthorization();
    }

    /**
     * 在完整 Bean 图形成后校验认证入口及整条业务路径覆盖。
     */
    @Bean
    public SmartInitializingSingleton smartRedisLimiterPortalGuard(
            ConfigurableListableBeanFactory beans, SmartRedisLimiterManagementProperties properties) {
        return () -> {
            // 可选 Starter 不在类路径时先给出本模块配置异常，避免反射解析方法签名失败。
            ClassLoader classLoader = beans.getBeanClassLoader();
            if (!ClassUtils.isPresent(SmartRedisLimiterManagementConstant.RESOURCE_PROPERTIES_CLASS, classLoader)
                    || !ClassUtils.isPresent(SmartRedisLimiterManagementConstant.RESOURCE_ENGINE_CLASS, classLoader)) {
                throw resourceRequired();
            }
            try {
                Class<?> propertiesClass = ClassUtils.forName(
                        SmartRedisLimiterManagementConstant.RESOURCE_PROPERTIES_CLASS, classLoader);
                Class<?> engineClass = ClassUtils.forName(
                        SmartRedisLimiterManagementConstant.RESOURCE_ENGINE_CLASS, classLoader);
                Object resource = beans.getBeanProvider(propertiesClass).getIfAvailable();
                Object security = resource == null ? null : propertiesClass.getMethod("getSecurity").invoke(resource);
                String basePath = properties.getApi().getBasePath();
                String path = ("/".equals(basePath) ? "" : basePath) + "/v1/policy/**";
                // 走查诊断：逐条输出门禁条件（仅含 Bean 存在性与路径匹配结论，无敏感数据）
                boolean c1 = resource != null && (Boolean) propertiesClass.getMethod("isEnabled").invoke(resource);
                boolean c2 = beans.getBeanProvider(engineClass).getIfAvailable() != null;
                boolean c3 = containsProtectedPath(security, path);
                boolean c4 = permitAllPathsEmpty(security);
                boolean c5 = beans.containsBean(SmartRedisLimiterManagementConstant.RESOURCE_SECURITY_CHAIN_BEAN)
                        && beans.isTypeMatch(SmartRedisLimiterManagementConstant.RESOURCE_SECURITY_CHAIN_BEAN, SecurityFilterChain.class);
                boolean c6 = beans.containsBean(SmartRedisLimiterManagementConstant.RESOURCE_MVC_BEAN)
                        && beans.isTypeMatch(SmartRedisLimiterManagementConstant.RESOURCE_MVC_BEAN, WebMvcConfigurer.class);
                int adapterCount = beans.getBeansOfType(ResourceAuthenticationAdapter.class).size();
                log.warn("Portal 门禁诊断 propsEnabled={} engine={} protectedPath={} permitAllEmpty={} chainBean={} mvcBean={} adapters={}",
                        c1, c2, c3, c4, c5, c6, adapterCount);
                if (!c1 || !c2 || !c3 || !c4 || !c5 || !c6
                        || adapterCount < SmartRedisLimiterManagementConstant.PORTAL_MIN_SOURCE_COUNT) {
                    throw resourceRequired();
                }
            } catch (SmartRedisLimiterManagementConfigurationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw resourceRequired();
            }
        };
    }
}
