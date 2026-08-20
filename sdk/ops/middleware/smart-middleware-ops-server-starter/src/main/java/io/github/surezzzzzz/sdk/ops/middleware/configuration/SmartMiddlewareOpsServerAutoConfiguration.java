package io.github.surezzzzzz.sdk.ops.middleware.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceAutoConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteConfiguration;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteJdbcTemplateAliasConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteManagedDatasourceConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.ops.middleware.SmartMiddlewareOpsServerPackage;
import io.github.surezzzzzz.sdk.ops.middleware.annotation.SmartMiddlewareOpsServerComponent;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.audit.PersistenceEngineMiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentityResolver;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.SpringSecurityMiddlewareOpsIdentityResolver;
import io.github.surezzzzzz.sdk.ops.middleware.authorization.AuthenticatedAllowAllMiddlewareOpsAuthorizationPolicy;
import io.github.surezzzzzz.sdk.ops.middleware.authorization.MiddlewareOpsAuthorizationPolicy;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsResponseHeaderFilter;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter.DefaultElasticsearchOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter.ElasticsearchOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog.ElasticsearchIndexListExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog.ElasticsearchIndexListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary.ElasticsearchSummaryExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary.ElasticsearchSummaryRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.DefaultKafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.KafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail.KafkaConsumerGroupDetailExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail.KafkaConsumerGroupDetailRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag.KafkaConsumerGroupLagListExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag.KafkaConsumerGroupLagListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.list.KafkaConsumerGroupListExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.list.KafkaConsumerGroupListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.datasource.KafkaDatasourceListExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.datasource.KafkaDatasourceListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config.KafkaTopicConfigExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config.KafkaTopicConfigRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list.KafkaTopicListExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list.KafkaTopicListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime.KafkaTopicRuntimeExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime.KafkaTopicRuntimeRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.DefaultMysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.MysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource.MysqlDatasourceStatusExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.datasource.MysqlDatasourceStatusRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlExplainRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.table.*;
import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.DefaultRedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.RedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.redis.datasource.RedisDatasourceListExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.redis.datasource.RedisDatasourceListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata.RedisKeyMetadataExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata.RedisKeyMetadataRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.redis.summary.RedisSummaryExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.redis.summary.RedisSummaryRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.service.*;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsConcurrencyGuard;
import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;

import java.util.List;

/**
 * Middleware Ops Server 默认完整运行链自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter({SimpleElasticsearchPersistenceAutoConfiguration.class, SimpleElasticsearchRouteConfiguration.class,
        SimpleRedisRouteConfiguration.class, SimpleKafkaRouteConfiguration.class, SimpleMysqlRouteManagedDatasourceConfiguration.class,
        SimpleMysqlRouteJdbcTemplateAliasConfiguration.class})
@EnableConfigurationProperties(SmartMiddlewareOpsServerProperties.class)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.ops.middleware", name = "enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(MiddlewareOpsServerEngine.class)
@ComponentScan(basePackageClasses = SmartMiddlewareOpsServerPackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
                classes = SmartMiddlewareOpsServerComponent.class))
public class SmartMiddlewareOpsServerAutoConfiguration {

    /**
     * 注册页面静态资源映射。
     */
    @Bean
    public MiddlewareOpsWebMvcConfiguration middlewareOpsWebMvcConfiguration(
            SmartMiddlewareOpsServerProperties properties) {
        return new MiddlewareOpsWebMvcConfiguration(properties);
    }

    /**
     * 注册 Windows AD LDAP 认证提供器。
     */
    @Bean
    @ConditionalOnProperty(prefix = io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant.LDAP_CONFIG_PREFIX,
            name = "enabled", havingValue = "true")
    public AuthenticationProvider middlewareOpsLdapAuthenticationProvider(SmartMiddlewareOpsServerProperties properties) {
        SmartMiddlewareOpsServerProperties.Ldap ldap = properties.getLdap();
        DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(ldap.getUrl());
        contextSource.setUserDn(ldap.getManagerDn());
        contextSource.setPassword(ldap.getManagerPassword());
        contextSource.afterPropertiesSet();
        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserSearch(new FilterBasedLdapUserSearch(ldap.getUserSearchBase(),
                ldap.getUserSearchFilter(), contextSource));
        return new LdapAuthenticationProvider(authenticator);
    }

    /**
     * 注册 API 认证边界。
     */
    @Bean
    @ConditionalOnBean(name = "middlewareOpsLdapAuthenticationProvider")
    @ConditionalOnProperty(prefix = io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant.LDAP_CONFIG_PREFIX,
            name = "enabled", havingValue = "true")
    public MiddlewareOpsApiSecurityConfiguration middlewareOpsApiSecurityConfiguration(
            @Qualifier("middlewareOpsLdapAuthenticationProvider") AuthenticationProvider authenticationProvider,
            SmartMiddlewareOpsServerProperties properties) {
        return new MiddlewareOpsApiSecurityConfiguration(authenticationProvider, properties);
    }

    /**
     * 注册页面认证边界。
     */
    @Bean
    @ConditionalOnBean(name = "middlewareOpsLdapAuthenticationProvider")
    @ConditionalOnProperty(prefix = io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant.LDAP_CONFIG_PREFIX,
            name = "enabled", havingValue = "true")
    public MiddlewareOpsUiSecurityConfiguration middlewareOpsUiSecurityConfiguration(
            @Qualifier("middlewareOpsLdapAuthenticationProvider") AuthenticationProvider authenticationProvider,
            SmartMiddlewareOpsServerProperties properties) {
        return new MiddlewareOpsUiSecurityConfiguration(authenticationProvider, properties);
    }

    /**
     * 注册 Ops 路径响应安全头过滤器。
     */
    @Bean
    public MiddlewareOpsResponseHeaderFilter middlewareOpsResponseHeaderFilter(
            SmartMiddlewareOpsServerProperties properties) {
        return new MiddlewareOpsResponseHeaderFilter(properties);
    }

    /**
     * 注册启动期安全数据源目录执行器。
     */
    @Bean
    public DatasourceCatalogExecutor datasourceCatalogExecutor(
            ObjectProvider<SimpleElasticsearchRouteRegistry> elasticsearchRegistryProvider,
            ObjectProvider<SimpleRedisRouteRegistry> redisRegistryProvider,
            ObjectProvider<SimpleKafkaRouteRegistry> kafkaRegistryProvider,
            ObjectProvider<SimpleMysqlRouteRegistry> mysqlRegistryProvider,
            SmartMiddlewareOpsServerProperties properties) {
        return new DatasourceCatalogExecutor(elasticsearchRegistryProvider.getIfAvailable(),
                redisRegistryProvider.getIfAvailable(), kafkaRegistryProvider.getIfAvailable(),
                mysqlRegistryProvider.getIfAvailable(), properties.getDatasourceTags());
    }

    /**
     * 注册数据源目录请求校验器。
     */
    @Bean
    public DatasourceCatalogRequestValidator datasourceCatalogRequestValidator() {
        return new DatasourceCatalogRequestValidator();
    }

    /**
     * 注册一期 Spring Security 身份解析边界。
     */
    @Bean
    @ConditionalOnMissingBean(MiddlewareOpsIdentityResolver.class)
    public MiddlewareOpsIdentityResolver middlewareOpsIdentityResolver() {
        return new SpringSecurityMiddlewareOpsIdentityResolver();
    }

    /**
     * 注册一期已认证用户全量只读授权策略。
     */
    @Bean
    @ConditionalOnMissingBean(MiddlewareOpsAuthorizationPolicy.class)
    public MiddlewareOpsAuthorizationPolicy middlewareOpsAuthorizationPolicy() {
        return new AuthenticatedAllowAllMiddlewareOpsAuthorizationPolicy();
    }

    /**
     * 注册两级瞬时并发守卫。
     */
    @Bean
    public MiddlewareOpsConcurrencyGuard middlewareOpsConcurrencyGuard(SmartMiddlewareOpsServerProperties properties) {
        return new MiddlewareOpsConcurrencyGuard(properties.getConcurrency().getGlobal(),
                properties.getConcurrency().getDatasource());
    }

    /**
     * 注册 Elasticsearch Route 安全适配器。
     */
    @Bean
    @ConditionalOnBean(SimpleElasticsearchRouteRegistry.class)
    public ElasticsearchOperationsViewAdapter elasticsearchOperationsViewAdapter(SimpleElasticsearchRouteRegistry registry,
                                                                                 SmartMiddlewareOpsServerProperties properties) {
        return new DefaultElasticsearchOperationsViewAdapter(registry, properties.getQuery().getDeadlineMillis());
    }

    /**
     * 注册 Redis Route 安全适配器。
     */
    @Bean
    @ConditionalOnBean(SimpleRedisRouteRegistry.class)
    public RedisOperationsViewAdapter redisOperationsViewAdapter(SimpleRedisRouteRegistry registry,
                                                                 SmartMiddlewareOpsServerProperties properties) {
        return new DefaultRedisOperationsViewAdapter(registry, properties.getQuery().getDeadlineMillis(),
                properties.getQuery().getMaxResponseLength());
    }

    /**
     * 注册 Kafka Route 安全适配器。
     */
    @Bean
    @ConditionalOnBean({SimpleKafkaRouteRegistry.class, KafkaRouteDiagnostics.class, KafkaRouteAdminClientFactory.class})
    public KafkaOperationsViewAdapter kafkaOperationsViewAdapter(SimpleKafkaRouteRegistry registry,
                                                                 KafkaRouteDiagnostics diagnostics,
                                                                 KafkaRouteAdminClientFactory adminClientFactory,
                                                                 SmartMiddlewareOpsServerProperties properties) {
        return new DefaultKafkaOperationsViewAdapter(registry, diagnostics, adminClientFactory,
                properties.getQuery().getDeadlineMillis(), properties.getQuery().getMaxSize());
    }

    /**
     * 注册 MySQL Route 安全适配器。
     */
    @Bean
    @ConditionalOnBean({SimpleMysqlRouteRegistry.class, MySqlRouteTemplate.class})
    public MysqlOperationsViewAdapter mysqlOperationsViewAdapter(SimpleMysqlRouteRegistry registry,
                                                                 MySqlRouteTemplate routeTemplate,
                                                                 SmartMiddlewareOpsServerProperties properties) {
        return new DefaultMysqlOperationsViewAdapter(registry, routeTemplate, properties.getQuery().getDeadlineMillis(),
                properties.getQuery().getMaxColumns(), properties.getQuery().getMaxCellLength(),
                properties.getQuery().getMaxResponseLength());
    }

    /**
     * 注册 MySQL 数据源状态探测执行器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlDatasourceStatusExecutor mysqlDatasourceStatusExecutor(MysqlOperationsViewAdapter adapter) {
        return new MysqlDatasourceStatusExecutor(adapter);
    }

    /**
     * 注册 MySQL 数据源状态探测请求校验器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlDatasourceStatusRequestValidator mysqlDatasourceStatusRequestValidator() {
        return new MysqlDatasourceStatusRequestValidator();
    }

    /**
     * 注册 MySQL 受控 SELECT 执行器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlSelectExecutor mysqlSelectExecutor(MysqlOperationsViewAdapter adapter) {
        return new MysqlSelectExecutor(adapter);
    }

    /**
     * 注册 MySQL 受控 SELECT 请求校验器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlSelectRequestValidator mysqlSelectRequestValidator(SmartMiddlewareOpsServerProperties properties) {
        return new MysqlSelectRequestValidator(properties.getQuery().getMaxSqlLength(), properties.getQuery().getMaxSize(),
                properties.getQuery().getMaxColumns());
    }

    /**
     * 注册 MySQL 受控 EXPLAIN 执行器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlExplainExecutor mysqlExplainExecutor(MysqlOperationsViewAdapter adapter) {
        return new MysqlExplainExecutor(adapter);
    }

    /**
     * 注册 MySQL 受控 EXPLAIN 请求校验器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlExplainRequestValidator mysqlExplainRequestValidator(SmartMiddlewareOpsServerProperties properties) {
        return new MysqlExplainRequestValidator(properties.getQuery().getMaxSqlLength(),
                properties.getQuery().getMaxColumns());
    }

    /**
     * 注册 MySQL 表和视图目录执行器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlTableListExecutor mysqlTableListExecutor(MysqlOperationsViewAdapter adapter) {
        return new MysqlTableListExecutor(adapter);
    }

    /**
     * 注册 MySQL 表和视图目录请求校验器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlTableListRequestValidator mysqlTableListRequestValidator(SmartMiddlewareOpsServerProperties properties) {
        return new MysqlTableListRequestValidator(properties.getQuery().getMaxResourceNameLength(),
                properties.getQuery().getMaxSize());
    }

    /**
     * 注册 MySQL 列目录执行器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlTableColumnsExecutor mysqlTableColumnsExecutor(MysqlOperationsViewAdapter adapter) {
        return new MysqlTableColumnsExecutor(adapter);
    }

    /**
     * 注册 MySQL 列目录请求校验器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlTableColumnsRequestValidator mysqlTableColumnsRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new MysqlTableColumnsRequestValidator(properties.getQuery().getMaxResourceNameLength());
    }

    /**
     * 注册 MySQL 索引目录执行器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlTableIndexesExecutor mysqlTableIndexesExecutor(MysqlOperationsViewAdapter adapter) {
        return new MysqlTableIndexesExecutor(adapter);
    }

    /**
     * 注册 MySQL 索引目录请求校验器。
     */
    @Bean
    @ConditionalOnBean(MysqlOperationsViewAdapter.class)
    public MysqlTableIndexesRequestValidator mysqlTableIndexesRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new MysqlTableIndexesRequestValidator(properties.getQuery().getMaxResourceNameLength());
    }

    /**
     * 注册 Elasticsearch 摘要执行器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchSummaryExecutor elasticsearchSummaryExecutor(ElasticsearchOperationsViewAdapter adapter) {
        return new ElasticsearchSummaryExecutor(adapter);
    }

    /**
     * 注册 Elasticsearch 摘要请求校验器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchSummaryRequestValidator elasticsearchSummaryRequestValidator() {
        return new ElasticsearchSummaryRequestValidator();
    }

    /**
     * 注册 Elasticsearch 索引目录执行器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchIndexListExecutor elasticsearchIndexListExecutor(ElasticsearchOperationsViewAdapter adapter) {
        return new ElasticsearchIndexListExecutor(adapter);
    }

    /**
     * 注册 Elasticsearch 索引目录请求校验器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchIndexListRequestValidator elasticsearchIndexListRequestValidator() {
        return new ElasticsearchIndexListRequestValidator();
    }

    /**
     * 注册 Elasticsearch 字段能力目录执行器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchFieldCapabilitiesExecutor elasticsearchFieldCapabilitiesExecutor(
            ElasticsearchOperationsViewAdapter adapter) {
        return new ElasticsearchFieldCapabilitiesExecutor(adapter);
    }

    /**
     * 注册 Elasticsearch 字段能力目录请求校验器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchFieldCapabilitiesRequestValidator elasticsearchFieldCapabilitiesRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new ElasticsearchFieldCapabilitiesRequestValidator(properties.getQuery().getMaxResourceNameLength());
    }

    /**
     * 注册 Elasticsearch JSON DSL 执行器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchDocumentQueryExecutor elasticsearchDocumentQueryExecutor(ElasticsearchOperationsViewAdapter adapter) {
        return new ElasticsearchDocumentQueryExecutor(adapter);
    }

    /**
     * 注册 Elasticsearch JSON DSL 请求校验器。
     */
    @Bean
    @ConditionalOnBean(ElasticsearchOperationsViewAdapter.class)
    public ElasticsearchDocumentQueryRequestValidator elasticsearchDocumentQueryRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new ElasticsearchDocumentQueryRequestValidator(properties.getQuery().getMaxResourceNameLength(),
                properties.getQuery().getMaxDslLength(), properties.getQuery().getMaxSize(),
                properties.getQuery().getElasticsearchMaxOffset(), new ObjectMapper());
    }

    /**
     * 注册 Redis 数据源清单执行器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisDatasourceListExecutor redisDatasourceListExecutor(RedisOperationsViewAdapter adapter) {
        return new RedisDatasourceListExecutor(adapter);
    }

    /**
     * 注册 Redis 数据源清单请求校验器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisDatasourceListRequestValidator redisDatasourceListRequestValidator() {
        return new RedisDatasourceListRequestValidator();
    }

    /**
     * 注册 Redis 摘要执行器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisSummaryExecutor redisSummaryExecutor(RedisOperationsViewAdapter adapter) {
        return new RedisSummaryExecutor(adapter);
    }

    /**
     * 注册 Redis 摘要请求校验器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisSummaryRequestValidator redisSummaryRequestValidator() {
        return new RedisSummaryRequestValidator();
    }

    /**
     * 注册 Redis 字面量前缀 key 发现执行器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisKeyDiscoveryExecutor redisKeyDiscoveryExecutor(RedisOperationsViewAdapter adapter) {
        return new RedisKeyDiscoveryExecutor(adapter);
    }

    /**
     * 注册 Redis 字面量前缀 key 发现请求校验器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisKeyDiscoveryRequestValidator redisKeyDiscoveryRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new RedisKeyDiscoveryRequestValidator(properties.getQuery().getMaxResourceNameLength(),
                properties.getQuery().getMaxSize());
    }

    /**
     * 注册 Redis 精确 key 元数据执行器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisKeyMetadataExecutor redisKeyMetadataExecutor(RedisOperationsViewAdapter adapter) {
        return new RedisKeyMetadataExecutor(adapter);
    }

    /**
     * 注册 Redis 精确 key 元数据请求校验器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisKeyMetadataRequestValidator redisKeyMetadataRequestValidator() {
        return new RedisKeyMetadataRequestValidator();
    }

    /**
     * 注册 Redis 精确 key 类型化读取执行器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisKeyReadExecutor redisKeyReadExecutor(RedisOperationsViewAdapter adapter) {
        return new RedisKeyReadExecutor(adapter);
    }

    /**
     * 注册 Redis 精确 key 类型化读取请求校验器。
     */
    @Bean
    @ConditionalOnBean(RedisOperationsViewAdapter.class)
    public RedisKeyReadRequestValidator redisKeyReadRequestValidator(SmartMiddlewareOpsServerProperties properties) {
        return new RedisKeyReadRequestValidator(properties.getQuery().getMaxResourceNameLength(),
                properties.getQuery().getMaxSize());
    }

    /**
     * 注册 Kafka 数据源清单执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaDatasourceListExecutor kafkaDatasourceListExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaDatasourceListExecutor(adapter);
    }

    /**
     * 注册 Kafka 数据源清单请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaDatasourceListRequestValidator kafkaDatasourceListRequestValidator() {
        return new KafkaDatasourceListRequestValidator();
    }

    /**
     * 注册 Kafka topic 清单执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaTopicListExecutor kafkaTopicListExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaTopicListExecutor(adapter);
    }

    /**
     * 注册 Kafka topic 清单请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaTopicListRequestValidator kafkaTopicListRequestValidator(SmartMiddlewareOpsServerProperties properties) {
        return new KafkaTopicListRequestValidator(properties.getQuery().getMaxSize());
    }

    /**
     * 注册 Kafka 消费组清单执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaConsumerGroupListExecutor kafkaConsumerGroupListExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaConsumerGroupListExecutor(adapter);
    }

    /**
     * 注册 Kafka 消费组清单请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaConsumerGroupListRequestValidator kafkaConsumerGroupListRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new KafkaConsumerGroupListRequestValidator(properties.getQuery().getMaxSize());
    }

    /**
     * 注册 Kafka Topic 固定配置执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaTopicConfigExecutor kafkaTopicConfigExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaTopicConfigExecutor(adapter);
    }

    /**
     * 注册 Kafka Topic 固定配置请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaTopicConfigRequestValidator kafkaTopicConfigRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new KafkaTopicConfigRequestValidator(properties.getQuery().getMaxResourceNameLength());
    }

    /**
     * 注册 Kafka 消费组安全详情执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaConsumerGroupDetailExecutor kafkaConsumerGroupDetailExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaConsumerGroupDetailExecutor(adapter);
    }

    /**
     * 注册 Kafka 消费组安全详情请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaConsumerGroupDetailRequestValidator kafkaConsumerGroupDetailRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new KafkaConsumerGroupDetailRequestValidator(properties.getQuery().getMaxResourceNameLength());
    }

    /**
     * 注册 Kafka Topic 分区状态执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaTopicRuntimeExecutor kafkaTopicRuntimeExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaTopicRuntimeExecutor(adapter);
    }

    /**
     * 注册 Kafka Topic 分区状态请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaTopicRuntimeRequestValidator kafkaTopicRuntimeRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new KafkaTopicRuntimeRequestValidator(properties.getQuery().getMaxResourceNameLength());
    }

    /**
     * 注册 Kafka 消费组积压执行器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaConsumerGroupLagListExecutor kafkaConsumerGroupLagListExecutor(KafkaOperationsViewAdapter adapter) {
        return new KafkaConsumerGroupLagListExecutor(adapter);
    }

    /**
     * 注册 Kafka 消费组积压请求校验器。
     */
    @Bean
    @ConditionalOnBean(KafkaOperationsViewAdapter.class)
    public KafkaConsumerGroupLagListRequestValidator kafkaConsumerGroupLagListRequestValidator(
            SmartMiddlewareOpsServerProperties properties) {
        return new KafkaConsumerGroupLagListRequestValidator(properties.getQuery().getMaxSize());
    }

    /**
     * 注册类型化执行器注册表。
     */
    @Bean
    public MiddlewareOpsExecutorRegistry middlewareOpsExecutorRegistry(List<MiddlewareOpsExecutor<?, ?>> executors,
                                                                       List<MiddlewareOpsRequestValidator<?>> validators) {
        return new DefaultMiddlewareOpsExecutorRegistry(executors, validators);
    }

    /**
     * 注册基于 PersistenceEngine 的异步审计发布器。
     */
    @Bean
    @ConditionalOnBean(PersistenceEngine.class)
    @ConditionalOnProperty(prefix = SmartMiddlewareOpsServerConstant.CONFIG_PREFIX + ".audit", name = "write-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MiddlewareOpsAuditPublisher.class)
    public MiddlewareOpsAuditPublisher persistenceEngineMiddlewareOpsAuditPublisher(PersistenceEngine persistenceEngine) {
        return new PersistenceEngineMiddlewareOpsAuditPublisher(persistenceEngine);
    }

    /**
     * 注册默认唯一编排入口。
     */
    @Bean
    public MiddlewareOpsServerEngine middlewareOpsServerEngine(MiddlewareOpsIdentityResolver identityResolver,
                                                               MiddlewareOpsAuthorizationPolicy authorizationPolicy,
                                                               MiddlewareOpsExecutorRegistry registry,
                                                               MiddlewareOpsConcurrencyGuard concurrencyGuard,
                                                               ObjectProvider<MiddlewareOpsAuditPublisher> auditPublisherProvider,
                                                               DatasourceCatalogExecutor datasourceCatalogExecutor) {
        return new DefaultMiddlewareOpsServerEngine(identityResolver, authorizationPolicy, registry, concurrencyGuard,
                auditPublisherProvider.getIfAvailable(), datasourceCatalogExecutor);
    }
}
