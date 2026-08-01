package io.github.surezzzzzz.sdk.mysql.route.configuration;

import io.github.surezzzzzz.sdk.mysql.route.SimpleMysqlRoutePackage;
import io.github.surezzzzzz.sdk.mysql.route.annotation.SimpleMysqlRouteComponent;
import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.audit.NoopMySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.credential.MySqlRouteCredentialResolver;
import io.github.surezzzzzz.sdk.mysql.route.datasource.DefaultMySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.resolver.DefaultMySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.resolver.MySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.mysql.route.validator.MySqlRoutePropertiesValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * MySQL Route 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleMysqlRouteProperties.class)
@ComponentScan(basePackageClasses = SimpleMysqlRoutePackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleMysqlRouteComponent.class))
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = SimpleMysqlRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleMysqlRouteConfiguration {

    /**
     * 注册默认配置校验器。
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
     * 注册默认物理数据源工厂。
     *
     * @return 物理数据源工厂
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteDataSourceFactory.class)
    public MySqlRouteDataSourceFactory mySqlRouteDataSourceFactory() {
        return new DefaultMySqlRouteDataSourceFactory();
    }

    /**
     * 注册默认空审计发布器。
     *
     * @return 空审计发布器
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteAuditPublisher.class)
    public MySqlRouteAuditPublisher mySqlRouteAuditPublisher() {
        return new NoopMySqlRouteAuditPublisher();
    }

    /**
     * 初始化固定目标注册表。
     *
     * @param properties         Route 配置
     * @param validator          配置校验器
     * @param credentialResolver 凭据解析器
     * @param dataSourceFactory  物理数据源工厂
     * @return 固定目标注册表
     */
    @Bean
    @ConditionalOnMissingBean(SimpleMysqlRouteRegistry.class)
    public SimpleMysqlRouteRegistry simpleMysqlRouteRegistry(SimpleMysqlRouteProperties properties,
                                                             MySqlRoutePropertiesValidator validator,
                                                             MySqlRouteCredentialResolver credentialResolver,
                                                             MySqlRouteDataSourceFactory dataSourceFactory) {
        return new SimpleMysqlRouteRegistry(properties, validator, credentialResolver, dataSourceFactory);
    }

    /**
     * 注册固定名称的路由数据源。
     *
     * @param registry 目标注册表
     * @return 路由数据源
     */
    @Bean(name = SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME)
    public DataSource mysqlRouteRoutingDataSource(SimpleMysqlRouteRegistry registry) {
        Map<Object, Object> targets = registry.routingTargets();
        return new MySqlRoutingDataSource(targets);
    }

    /**
     * 注册固定名称的路由 JdbcTemplate。
     *
     * @param routingDataSource 路由数据源
     * @return 路由 JdbcTemplate
     */
    @Bean(name = SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME)
    public JdbcTemplate mysqlRouteJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME)
            DataSource routingDataSource) {
        return new JdbcTemplate(routingDataSource);
    }

    /**
     * 注册固定名称的路由 NamedParameterJdbcTemplate。
     *
     * @param routingDataSource 路由数据源
     * @return 路由 NamedParameterJdbcTemplate
     */
    @Bean(name = SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME)
    public NamedParameterJdbcTemplate mysqlRouteNamedParameterJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME)
            DataSource routingDataSource) {
        return new NamedParameterJdbcTemplate(routingDataSource);
    }

    /**
     * 注册默认业务路由解析器。
     *
     * @param properties     Route 配置
     * @param patternMatcher 路由规则匹配器
     * @return 业务路由解析器
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteResolver.class)
    public MySqlRouteResolver mySqlRouteResolver(SimpleMysqlRouteProperties properties,
                                                 io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher patternMatcher) {
        return new DefaultMySqlRouteResolver(properties, patternMatcher);
    }

    /**
     * 注册显式 Route 执行模板。
     *
     * @param registry                   目标注册表
     * @param resolver                   业务路由解析器
     * @param routingJdbcTemplate        路由 JdbcTemplate
     * @param namedParameterJdbcTemplate 路由命名参数模板
     * @param auditPublisher             审计事件发布器
     * @return Route 执行模板
     */
    @Bean
    @ConditionalOnMissingBean(MySqlRouteTemplate.class)
    public MySqlRouteTemplate mySqlRouteTemplate(SimpleMysqlRouteRegistry registry,
                                                 MySqlRouteResolver resolver,
                                                 @org.springframework.beans.factory.annotation.Qualifier(SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME)
                                                 JdbcTemplate routingJdbcTemplate,
                                                 @org.springframework.beans.factory.annotation.Qualifier(SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME)
                                                 NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                                 MySqlRouteAuditPublisher auditPublisher) {
        return new MySqlRouteTemplate(registry, resolver, routingJdbcTemplate, namedParameterJdbcTemplate, auditPublisher);
    }
}
