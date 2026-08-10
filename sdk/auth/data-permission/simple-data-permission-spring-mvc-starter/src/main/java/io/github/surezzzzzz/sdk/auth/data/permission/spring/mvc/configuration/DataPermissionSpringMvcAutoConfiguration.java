package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.configuration;

import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentSource;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DefaultDataPermissionEvaluator;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.SimpleDataPermissionSpringMvcPackage;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.SimpleDataPermissionSpringMvcComponent;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.constant.SimpleDataPermissionSpringMvcConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DefaultDataPermissionFacade;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.VerifiedResourceDataGrantDocumentSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC数据权限自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(DataPermissionSpringMvcProperties.class)
@ComponentScan(
        basePackageClasses = SimpleDataPermissionSpringMvcPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleDataPermissionSpringMvcComponent.class),
        useDefaultFilters = false
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = SimpleDataPermissionSpringMvcConstant.CONFIG_PREFIX,
        name = SimpleDataPermissionSpringMvcConstant.CONFIG_NAME_ENABLED, havingValue = "true", matchIfMissing = true)
public class DataPermissionSpringMvcAutoConfiguration {

    /**
     * 创建默认数据权限评估器。
     *
     * @return 数据权限评估器
     */
    @Bean
    @ConditionalOnMissingBean
    public DataPermissionEvaluator dataPermissionEvaluator() {
        return new DefaultDataPermissionEvaluator();
    }

    /**
     * 创建已验证授权文档来源。
     *
     * @return 已验证授权文档来源
     */
    @Bean
    @ConditionalOnMissingBean(DataGrantDocumentSource.class)
    public DataGrantDocumentSource verifiedResourceDataGrantDocumentSource() {
        return new VerifiedResourceDataGrantDocumentSource();
    }

    /**
     * 创建默认数据权限门面。
     *
     * @param evaluator 数据权限评估器
     * @param source    已验证授权文档来源
     * @return 数据权限门面
     */
    @Bean
    @ConditionalOnMissingBean
    public DataPermissionFacade dataPermissionFacade(DataPermissionEvaluator evaluator, DataGrantDocumentSource source) {
        return new DefaultDataPermissionFacade(evaluator, source);
    }

    /**
     * 创建MVC数据权限配置。
     *
     * @param facade 数据权限门面
     * @return MVC数据权限配置
     */
    @Bean
    public WebMvcConfigurer dataPermissionSpringMvcConfigurer(DataPermissionFacade facade) {
        return new DataPermissionSpringMvcConfiguration(facade);
    }
}
