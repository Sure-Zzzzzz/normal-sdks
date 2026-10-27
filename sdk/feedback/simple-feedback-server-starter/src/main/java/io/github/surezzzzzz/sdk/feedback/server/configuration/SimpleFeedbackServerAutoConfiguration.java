package io.github.surezzzzzz.sdk.feedback.server.configuration;

import io.github.surezzzzzz.sdk.feedback.server.SimpleFeedbackServerPackage;
import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.controller.ApplicationController;
import io.github.surezzzzzz.sdk.feedback.server.controller.SubmissionController;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackConfigurationException;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackPrincipalResolver;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackResourceServerPrincipalResolver;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.support.FallbackAccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.support.FallbackSubmitterProfileProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriTemplate;

import javax.servlet.Filter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Simple Feedback Server Auto Configuration。
 *
 * <p>身份门槛说明：不再使用类级 @ConditionalOnBean——其在
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

    private static final String RESOURCE_AUTHENTICATION_FILTER_CLASS =
            "io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter";
    private static final String PROTECTED_PATHS_FIELD = "protectedPaths";
    private static final String SECURITY_FILTER_CHAIN_BEAN = "springSecurityFilterChain";
    private static final String REQUEST_MAPPING_BEAN = "requestMappingHandlerMapping";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    static void validateAssembly(FeedbackPrincipalResolver principalResolver, ApplicationContext context) {
        if (!(principalResolver instanceof FeedbackResourceServerPrincipalResolver)) {
            return;
        }
        FilterChainProxy filterChain;
        RequestMappingHandlerMapping handlerMapping;
        try {
            filterChain = context.getBean(SECURITY_FILTER_CHAIN_BEAN, FilterChainProxy.class);
            handlerMapping = context.getBean(REQUEST_MAPPING_BEAN, RequestMappingHandlerMapping.class);
        } catch (RuntimeException exception) {
            throw incompleteAssembly();
        }
        validateAssembly(principalResolver, filterChain, feedbackPaths(handlerMapping));
    }

    static void validateAssembly(FeedbackPrincipalResolver principalResolver, FilterChainProxy filterChain,
                                 Collection<String> feedbackPaths) {
        if (!(principalResolver instanceof FeedbackResourceServerPrincipalResolver)) {
            return;
        }
        if (filterChain == null || feedbackPaths.isEmpty()) {
            throw incompleteAssembly();
        }
        for (String path : feedbackPaths) {
            List<Filter> filters = filterChain.getFilters(path);
            if (filters == null || filters.stream().noneMatch(filter -> protects(filter, path))) {
                log.error("反馈资源认证链未覆盖端点：{}", path);
                throw incompleteAssembly();
            }
        }
    }

    static Set<String> feedbackPaths(RequestMappingHandlerMapping handlerMapping) {
        Set<String> paths = new LinkedHashSet<String>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            Class<?> type = entry.getValue().getBeanType();
            if (!ApplicationController.class.isAssignableFrom(type)
                    && !SubmissionController.class.isAssignableFrom(type)) {
                continue;
            }
            for (String pattern : patternValues(entry.getKey())) {
                UriTemplate template = new UriTemplate(pattern);
                Map<String, String> variables = new LinkedHashMap<String, String>();
                for (String variable : template.getVariableNames()) {
                    variables.put(variable, "1");
                }
                paths.add(template.expand(variables).getPath());
            }
        }
        return paths;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> patternValues(RequestMappingInfo mapping) {
        if (mapping.getPatternsCondition() != null) {
            return mapping.getPatternsCondition().getPatterns();
        }
        try {
            Method method = RequestMappingInfo.class.getMethod("getPatternValues");
            return (Collection<String>) method.invoke(mapping);
        } catch (ReflectiveOperationException exception) {
            throw incompleteAssembly();
        }
    }

    private static boolean protects(Filter filter, String path) {
        if (!RESOURCE_AUTHENTICATION_FILTER_CLASS.equals(filter.getClass().getName())) {
            return false;
        }
        Field protectedPathsField = ReflectionUtils.findField(filter.getClass(), PROTECTED_PATHS_FIELD);
        if (protectedPathsField == null) {
            return false;
        }
        ReflectionUtils.makeAccessible(protectedPathsField);
        Object configuredPaths = ReflectionUtils.getField(protectedPathsField, filter);
        if (!(configuredPaths instanceof Collection)) {
            return false;
        }
        for (Object protectedPath : (Collection<?>) configuredPaths) {
            if (protectedPath instanceof String && PATH_MATCHER.match((String) protectedPath, path)) {
                return true;
            }
        }
        return false;
    }

    private static FeedbackConfigurationException incompleteAssembly() {
        return new FeedbackConfigurationException(ErrorCode.COLLABORATION_ASSEMBLY_INCOMPLETE,
                ErrorMessage.COLLABORATION_ASSEMBLY_INCOMPLETE);
    }

    /**
     * SPI 降级实现：可访问应用集（散形态恒返回 null=不过滤；adaptor 覆盖）。
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
     * SPI 降级实现：提交人联系提示（散形态恒返回 null=空输入框；adaptor 覆盖）。
     *
     * @return 降级实现
     */
    @Bean
    @ConditionalOnMissingBean(SubmitterProfileProvider.class)
    public SubmitterProfileProvider fallbackSubmitterProfileProvider() {
        return new FallbackSubmitterProfileProvider();
    }

    /**
     * 协作装配完整性检查（启动期失败，避免运行时所有反馈请求退化为 503）。
     *
     * <p>默认资源认证桥需要公共资源服务过滤器在实际安全链内保护每个反馈端点。
     * 只检测类存在不能发现资源服务关闭、路径遗漏或其他链抢先匹配。宿主自备身份解析器免检。</p>
     *
     * @param principalResolver 反馈身份解析器
     * @param context           应用上下文
     * @return 装配完整性检查（单例装配完成后执行）
     */
    @Bean
    public SmartInitializingSingleton collaborationAssemblyCheck(FeedbackPrincipalResolver principalResolver,
                                                                 ApplicationContext context) {
        return () -> validateAssembly(principalResolver, context);
    }
}
