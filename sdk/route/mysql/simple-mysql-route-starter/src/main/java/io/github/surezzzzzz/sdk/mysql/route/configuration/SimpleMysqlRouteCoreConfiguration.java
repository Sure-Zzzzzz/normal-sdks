package io.github.surezzzzzz.sdk.mysql.route.configuration;

import io.github.surezzzzzz.sdk.mysql.route.SimpleMysqlRoutePackage;
import io.github.surezzzzzz.sdk.mysql.route.annotation.SimpleMysqlRouteComponent;
import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.audit.NoopMySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.datasource.DefaultMySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.resolver.DefaultMySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.resolver.MySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.validator.MySqlRoutePropertiesValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * MySQL Route 共享 Bean 配置。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleMysqlRouteProperties.class)
@ComponentScan(basePackageClasses = SimpleMysqlRoutePackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleMysqlRouteComponent.class))
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
public class SimpleMysqlRouteCoreConfiguration {

    /**
     * 创建 MySQL Route 配置校验器。
     *
     * @param patternMatcher 路由规则匹配器
     * @return 配置校验器
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRoutePropertiesValidator.class)
    public MySqlRoutePropertiesValidator mySqlRoutePropertiesValidator(
            io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher patternMatcher) {
        return new MySqlRoutePropertiesValidator(patternMatcher);
    }

    /**
     * 创建默认物理数据源工厂。
     *
     * @return 物理数据源工厂
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteDataSourceFactory.class)
    public MySqlRouteDataSourceFactory mySqlRouteDataSourceFactory() {
        return new DefaultMySqlRouteDataSourceFactory();
    }

    /**
     * 创建默认审计发布器。
     *
     * @return 审计发布器
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteAuditPublisher.class)
    public MySqlRouteAuditPublisher mySqlRouteAuditPublisher() {
        return new NoopMySqlRouteAuditPublisher();
    }

    /**
     * 创建 MySQL Route 目标注册表。
     *
     * @param properties        MySQL Route 配置
     * @param validator         配置校验器
     * @param dataSourceFactory 物理数据源工厂
     * @return 目标注册表
     */
    @Bean
    @ConditionalOnMissingBean(SimpleMysqlRouteRegistry.class)
    public SimpleMysqlRouteRegistry simpleMysqlRouteRegistry(SimpleMysqlRouteProperties properties,
                                                             MySqlRoutePropertiesValidator validator,
                                                             MySqlRouteDataSourceFactory dataSourceFactory) {
        return new SimpleMysqlRouteRegistry(properties, validator, dataSourceFactory);
    }

    /**
     * 创建 Spring 统一数据访问入口。
     *
     * @param registry   目标注册表
     * @param properties MySQL Route 配置
     * @return 严格路由数据源
     */
    @Bean(name = SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME)
    @Primary
    public MySqlRoutingDataSource mysqlRouteRoutingDataSource(SimpleMysqlRouteRegistry registry,
                                                              SimpleMysqlRouteProperties properties) {
        Map<Object, Object> targets = registry.routingTargets();
        return new MySqlRoutingDataSource(targets, properties.getPrimaryDatasource());
    }

    /**
     * 创建默认业务路由解析器。
     *
     * @param properties     MySQL Route 配置
     * @param patternMatcher 路由规则匹配器
     * @return 业务路由解析器
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteResolver.class)
    public MySqlRouteResolver mySqlRouteResolver(SimpleMysqlRouteProperties properties,
                                                 io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher patternMatcher) {
        return new DefaultMySqlRouteResolver(properties, patternMatcher);
    }
}
