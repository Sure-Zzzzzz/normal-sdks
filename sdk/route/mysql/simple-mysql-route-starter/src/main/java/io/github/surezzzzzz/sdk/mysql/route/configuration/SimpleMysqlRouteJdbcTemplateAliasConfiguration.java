package io.github.surezzzzzz.sdk.mysql.route.configuration;

import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.resolver.MySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MySQL Route JDBC 模板兼容名称配置。
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@ConditionalOnProperty(prefix = SimpleMysqlRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleMysqlRouteJdbcTemplateAliasConfiguration {

    /**
     * 将已发布的 Route 模板名称映射到 Spring Boot 标准模板。
     *
     * @return BeanFactory 后处理器
     */
    @Bean
    public static BeanFactoryPostProcessor mysqlRouteJdbcTemplateAliases() {
        return beanFactory -> {
            registerAlias(beanFactory, SimpleMysqlRouteConstant.BOOT_JDBC_TEMPLATE_BEAN_NAME,
                    SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME);
            registerAlias(beanFactory, SimpleMysqlRouteConstant.BOOT_NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME,
                    SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME);
        };
    }

    private static void registerAlias(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory,
                                      String beanName, String alias) {
        if (!beanFactory.containsBeanDefinition(beanName)) {
            throw new SimpleMysqlRouteException(ErrorCode.BOOT_JDBC_TEMPLATE_REQUIRED,
                    ErrorMessage.BOOT_JDBC_TEMPLATE_REQUIRED);
        }
        if (beanFactory.containsBean(alias)) {
            throw new SimpleMysqlRouteException(ErrorCode.ROUTING_BEAN_NAME_CONFLICT,
                    ErrorMessage.ROUTING_BEAN_NAME_CONFLICT);
        }
        beanFactory.registerAlias(beanName, alias);
    }

    /**
     * 注册显式 Route 执行模板。
     *
     * @param registry                   目标注册表
     * @param resolver                   业务路由解析器
     * @param routingDataSource          严格路由数据源
     * @param routingJdbcTemplate        标准路由 JdbcTemplate
     * @param namedParameterJdbcTemplate 标准路由命名参数模板
     * @param auditPublisher             审计事件发布器
     * @return Route 执行模板
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteTemplate.class)
    public MySqlRouteTemplate mySqlRouteTemplate(SimpleMysqlRouteRegistry registry,
                                                 MySqlRouteResolver resolver,
                                                 @Qualifier(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME)
                                                 MySqlRoutingDataSource routingDataSource,
                                                 org.springframework.jdbc.core.JdbcTemplate routingJdbcTemplate,
                                                 org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                                 MySqlRouteAuditPublisher auditPublisher) {
        return new MySqlRouteTemplate(registry, resolver, routingDataSource, routingJdbcTemplate,
                namedParameterJdbcTemplate, auditPublisher);
    }
}
