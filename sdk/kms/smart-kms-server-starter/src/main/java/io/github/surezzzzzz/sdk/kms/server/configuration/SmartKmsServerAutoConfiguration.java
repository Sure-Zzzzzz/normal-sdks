package io.github.surezzzzzz.sdk.kms.server.configuration;

import io.github.surezzzzzz.sdk.kms.core.repository.*;
import io.github.surezzzzzz.sdk.kms.core.service.*;
import io.github.surezzzzzz.sdk.kms.server.SmartKmsServerPackage;
import io.github.surezzzzzz.sdk.kms.server.annotation.SmartKmsServerComponent;
import io.github.surezzzzzz.sdk.kms.server.controller.*;
import io.github.surezzzzzz.sdk.kms.server.repository.*;
import io.github.surezzzzzz.sdk.kms.server.service.*;
import io.github.surezzzzzz.sdk.kms.server.support.KmsMyKeyHttpMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import java.security.SecureRandom;

/**
 * KMS Server 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SmartKmsServerProperties.class)
@ConditionalOnMissingBean(KmsServerEngine.class)
@ConditionalOnBean(KmsPrincipalResolver.class)
@ComponentScan(basePackageClasses = SmartKmsServerPackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
                classes = SmartKmsServerComponent.class))
public class SmartKmsServerAutoConfiguration {

    /**
     * 注册默认 KMS Server 完整替换边界标记。
     */
    @Bean
    public KmsServerEngine kmsServerEngine() {
        return new DefaultKmsServerEngine();
    }

    /**
     * 注册 KMS 可信边界使用的安全随机源。
     *
     * @return 默认安全随机源
     */
    @Bean
    @ConditionalOnMissingBean(SecureRandom.class)
    public SecureRandom kmsSecureRandom() {
        return new SecureRandom();
    }

    /**
     * 注册 KMS 内部材料生成器。
     *
     * @param secureRandom KMS 可信边界使用的安全随机源
     * @return 默认材料生成器
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyMaterialGenerator.class)
    public KmsKeyMaterialGenerator kmsKeyMaterialGenerator(SecureRandom secureRandom) {
        return new JcaKmsKeyMaterialGenerator(secureRandom);
    }

    /**
     * 注册 KMS 内部 JCA 密码学执行器。
     *
     * @param secureRandom KMS 可信边界使用的安全随机源
     * @return 默认 JCA 密码学执行器
     */
    @Bean
    @ConditionalOnMissingBean(KmsCryptoEngine.class)
    public JcaKmsCryptoEngine kmsCryptoEngine(SecureRandom secureRandom) {
        return new JcaKmsCryptoEngine(secureRandom);
    }

    /**
     * 将默认 JCA 执行器暴露为 SKMS 封装加密内部端口。
     *
     * @param cryptoEngine 默认 JCA 密码学执行器
     * @return 默认 SKMS 封装加密端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsEnvelopeEncryptionEngine.class)
    @ConditionalOnBean(JcaKmsCryptoEngine.class)
    public KmsEnvelopeEncryptionEngine kmsEnvelopeEncryptionEngine(JcaKmsCryptoEngine cryptoEngine) {
        return cryptoEngine;
    }

    /**
     * 注册使用当前事务连接查询数据库 UTC 时间的默认时钟。
     *
     * @param jdbcTemplate 执行数据库时间查询的 JDBC 模板
     * @return 默认 KMS 权威时间端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsClock.class)
    public KmsClock kmsClock(JdbcTemplate jdbcTemplate) {
        return new JdbcKmsClock(jdbcTemplate);
    }

    /**
     * 注册 owner 内逻辑密钥事务锁。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认逻辑密钥事务锁
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyLock.class)
    public KmsKeyLock kmsKeyLock(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsKeyLock(jdbcTemplate);
    }

    /**
     * 注册 owner 强隔离的默认逻辑密钥仓储。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认逻辑密钥仓储
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyRepository.class)
    public JdbcKmsKeyRepository kmsKeyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsKeyRepository(jdbcTemplate);
    }

    /**
     * 将默认逻辑密钥 JDBC 仓储暴露为管理列表内部端口。
     *
     * @param keyRepository 默认逻辑密钥 JDBC 仓储
     * @return 默认密钥管理列表端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyQueryRepository.class)
    @ConditionalOnBean(JdbcKmsKeyRepository.class)
    public KmsKeyQueryRepository kmsKeyQueryRepository(JdbcKmsKeyRepository keyRepository) {
        return keyRepository;
    }

    /**
     * 注册只在 KMS 可信边界读取材料的默认密钥版本仓储。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认密钥版本仓储
     */
    public KmsKeyVersionRepository kmsKeyVersionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return kmsJdbcKeyVersionRepository(jdbcTemplate);
    }

    /**
     * 显式暴露默认 JDBC 类型供同源查询条件识别，保留原工厂方法的二进制签名。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认密钥版本 JDBC 仓储
     */
    @Bean(name = "kmsKeyVersionRepository")
    @ConditionalOnMissingBean(KmsKeyVersionRepository.class)
    public JdbcKmsKeyVersionRepository kmsJdbcKeyVersionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsKeyVersionRepository(jdbcTemplate);
    }

    /**
     * 注册 owner 强隔离的默认密钥策略仓储。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认密钥策略仓储
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyPolicyRepository.class)
    public KmsKeyPolicyRepository kmsKeyPolicyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsKeyPolicyRepository(jdbcTemplate);
    }

    /**
     * 注册默认管理幂等仓储。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认管理幂等仓储
     */
    @Bean
    @ConditionalOnMissingBean(KmsIdempotencyRepository.class)
    public JdbcKmsIdempotencyRepository kmsIdempotencyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsIdempotencyRepository(jdbcTemplate);
    }

    /**
     * 将同一默认幂等仓储暴露为响应快照内部端口。
     *
     * @param idempotencyRepository 默认幂等 JDBC 仓储
     * @return 默认响应快照内部端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsIdempotencyResponseSnapshotRepository.class)
    @ConditionalOnBean(JdbcKmsIdempotencyRepository.class)
    public KmsIdempotencyResponseSnapshotRepository kmsIdempotencyResponseSnapshotRepository(
            JdbcKmsIdempotencyRepository idempotencyRepository) {
        return idempotencyRepository;
    }

    /**
     * 注册默认销毁任务仓储。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认销毁任务仓储
     */
    @Bean
    @ConditionalOnMissingBean(KmsDestructionJobRepository.class)
    public JdbcKmsDestructionJobRepository kmsDestructionJobRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsDestructionJobRepository(jdbcTemplate);
    }

    /**
     * 注册销毁任务管理页使用的 owner 隔离查询端口。
     *
     * <p>该端口只暴露无材料任务投影，不向已发布的 worker 核心仓储添加管理页专用方法。</p>
     *
     * @param jdbcTemplate 执行 owner 隔离 SQL 的 JDBC 模板
     * @return 默认销毁任务查询端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsDestructionJobQueryRepository.class)
    public KmsDestructionJobQueryRepository kmsDestructionJobQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsDestructionJobQueryRepository(jdbcTemplate);
    }

    /**
     * 将同一默认销毁任务仓储暴露为历史领取检查内部端口。
     *
     * @param destructionJobRepository 默认销毁任务 JDBC 仓储
     * @return 默认销毁取消历史领取检查端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsDestructionCancellationGuard.class)
    @ConditionalOnBean(JdbcKmsDestructionJobRepository.class)
    public KmsDestructionCancellationGuard kmsDestructionCancellationGuard(
            JdbcKmsDestructionJobRepository destructionJobRepository) {
        return destructionJobRepository;
    }

    /**
     * 只为内置同源密钥、版本和任务存储提供默认销毁明细，自定义存储自行适配。
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyDestructionQueryRepository.class)
    @ConditionalOnBean({JdbcKmsKeyRepository.class, JdbcKmsKeyVersionRepository.class, JdbcKmsDestructionJobRepository.class})
    public KmsKeyDestructionQueryRepository kmsKeyDestructionQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsKeyDestructionQueryRepository(jdbcTemplate);
    }

    /**
     * 按新增查询端口装配独立路由，不改变旧自定义宿主的控制器构造器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyDestructionQueryController.class)
    @ConditionalOnBean(KmsKeyDestructionQueryRepository.class)
    public KmsKeyDestructionQueryController kmsKeyDestructionQueryController(KmsPrincipalResolver principalResolver,
                                                                             SmartKmsServerProperties properties,
                                                                             KmsKeyDestructionQueryRepository destructionQueryRepository,
                                                                             KmsKeyQueryRepository keyQueryRepository) {
        return new KmsKeyDestructionQueryController(principalResolver, properties, destructionQueryRepository, keyQueryRepository);
    }

    /**
     * 注册提交后发布的 KMS 安全审计事件端口。
     *
     * @param applicationEventPublisher Spring 应用事件发布器
     * @return 默认安全审计事件发布端口
     */
    @Bean
    @ConditionalOnMissingBean(KmsEventPublisher.class)
    public KmsEventPublisher kmsEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringKmsEventPublisher(applicationEventPublisher);
    }

    /**
     * 注册跨实例串行化管理幂等首写的当前会话锁。
     */
    @Bean
    @ConditionalOnMissingBean(KmsIdempotencyScopeLock.class)
    public KmsIdempotencyScopeLock kmsIdempotencyScopeLock(JdbcTemplate jdbcTemplate) {
        return new JdbcKmsIdempotencyScopeLock(jdbcTemplate);
    }

    /**
     * 注册管理写操作的事务内幂等执行器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsManagementIdempotencyService.class)
    public KmsManagementIdempotencyService kmsManagementIdempotencyService(KmsClock clock,
                                                                           KmsIdempotencyRepository idempotencyRepository,
                                                                           KmsIdempotencyResponseSnapshotRepository snapshotRepository,
                                                                           KmsIdempotencyScopeLock scopeLock,
                                                                           SmartKmsServerProperties properties,
                                                                           KmsAuditPublisher auditPublisher) {
        return new KmsManagementIdempotencyService(clock, idempotencyRepository, snapshotRepository, scopeLock,
                properties, auditPublisher);
    }

    /**
     * 注册密码学 REST 控制器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsCryptoController.class)
    public KmsCryptoController kmsCryptoController(KmsPrincipalResolver principalResolver,
                                                   SmartKmsServerProperties properties,
                                                   CryptoOperationService cryptoOperationService,
                                                   KmsSignatureOperationService signatureOperationService) {
        return new KmsCryptoController(principalResolver, properties, cryptoOperationService, signatureOperationService);
    }

    /**
     * 注册逻辑密钥管理 REST 控制器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsKeyController.class)
    public KmsKeyController kmsKeyController(KmsPrincipalResolver principalResolver,
                                             SmartKmsServerProperties properties,
                                             KeyManagementService keyManagementService,
                                             KmsKeyQueryRepository keyQueryRepository,
                                             KeyPolicyManagementService keyPolicyManagementService,
                                             PublicKeyService publicKeyService,
                                             KmsManagementIdempotencyService idempotencyService,
                                             KmsPrincipalDisplayNameResolver displayNameResolver) {
        return new KmsKeyController(principalResolver, properties, keyManagementService, keyQueryRepository,
                keyPolicyManagementService, publicKeyService, idempotencyService, displayNameResolver);
    }

    /**
     * 注册可选的主体显示名解析端口；宿主可用自有目录实现替换，默认始终返回未解析。
     */
    @Bean
    @ConditionalOnMissingBean(KmsPrincipalDisplayNameResolver.class)
    public KmsPrincipalDisplayNameResolver kmsPrincipalDisplayNameResolver() {
        return new DefaultKmsPrincipalDisplayNameResolver();
    }

    /**
     * 只为内置同源密钥与策略存储提供默认跨钥策略分页查询，自定义存储自行适配。
     */
    @Bean
    @ConditionalOnMissingBean(KmsAdminPolicyQueryRepository.class)
    @ConditionalOnBean(JdbcKmsKeyRepository.class)
    public KmsAdminPolicyQueryRepository kmsAdminPolicyQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsAdminPolicyQueryRepository(jdbcTemplate);
    }

    /**
     * 按跨钥策略查询端口装配治理列表路由，缺端口时不装配。
     */
    @Bean
    @ConditionalOnMissingBean(KmsAdminPolicyQueryController.class)
    @ConditionalOnBean(KmsAdminPolicyQueryRepository.class)
    public KmsAdminPolicyQueryController kmsAdminPolicyQueryController(KmsPrincipalResolver principalResolver,
                                                                       SmartKmsServerProperties properties,
                                                                       KmsAdminPolicyQueryRepository policyQueryRepository,
                                                                       KmsPrincipalDisplayNameResolver displayNameResolver) {
        return new KmsAdminPolicyQueryController(principalResolver, properties, policyQueryRepository, displayNameResolver);
    }

    /**
     * 注册以 DataPlan 执行归属范围的管理密钥查询控制器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsAdminKeyQueryController.class)
    public KmsAdminKeyQueryController kmsAdminKeyQueryController(KmsPrincipalResolver principalResolver,
                                                                 SmartKmsServerProperties properties,
                                                                 KmsKeyQueryRepository keyQueryRepository,
                                                                 KmsPrincipalDisplayNameResolver displayNameResolver) {
        return new KmsAdminKeyQueryController(principalResolver, properties, keyQueryRepository, displayNameResolver);
    }

    /**
     * 注册销毁任务与当前 worker 健康 REST 控制器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsDestructionController.class)
    public KmsDestructionController kmsDestructionController(KmsPrincipalResolver principalResolver,
                                                             SmartKmsServerProperties properties,
                                                             KmsDestructionJobQueryRepository jobQueryRepository,
                                                             DestructionWorkerHealthService workerHealthService,
                                                             KmsDestructionWorkerLifecycle workerLifecycle,
                                                             KmsPrincipalDisplayNameResolver displayNameResolver) {
        return new KmsDestructionController(principalResolver, properties, jobQueryRepository,
                workerHealthService, workerLifecycle, displayNameResolver);
    }

    /**
     * 注册门户主体自省端点。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMeController.class)
    public KmsMeController kmsMeController(KmsPrincipalResolver principalResolver,
                                           SmartKmsServerProperties properties) {
        return new KmsMeController(principalResolver, properties);
    }

    /**
     * 注册个人工作区密钥端点（/me/keys，只读固定当前主体 owner）。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMyKeyQueryController.class)
    public KmsMyKeyQueryController kmsMyKeyQueryController(KmsPrincipalResolver principalResolver,
                                                           SmartKmsServerProperties properties,
                                                           io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyQueryRepository keyQueryRepository) {
        return new KmsMyKeyQueryController(principalResolver, properties, keyQueryRepository);
    }

    /**
     * 注册固定本人归属的生命周期写接口。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMyKeyManagementController.class)
    public KmsMyKeyManagementController kmsMyKeyManagementController(KmsPrincipalResolver principalResolver,
                                                                     SmartKmsServerProperties properties, KeyManagementService keyManagementService,
                                                                     KmsKeyQueryRepository keyQueryRepository, KmsManagementIdempotencyService idempotencyService) {
        return new KmsMyKeyManagementController(principalResolver, properties, keyManagementService,
                keyQueryRepository, idempotencyService);
    }

    /**
     * 注册仅支持明确 KMS DTO 的私有 JSON 转换器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMyKeyHttpMessageConverter.class)
    public KmsMyKeyHttpMessageConverter kmsMyKeyHttpMessageConverter() {
        return new KmsMyKeyHttpMessageConverter();
    }

    /**
     * 仅在默认路由生效时装配私有类型的 MVC 转换顺序。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMyKeyWebMvcConfigurer.class)
    public KmsMyKeyWebMvcConfigurer kmsMyKeyWebMvcConfigurer(KmsMyKeyHttpMessageConverter converter) {
        return new KmsMyKeyWebMvcConfigurer(converter);
    }

    /**
     * 注册 KMS REST 安全错误处理器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsHttpExceptionHandler.class)
    public KmsHttpExceptionHandler kmsHttpExceptionHandler(KmsPrincipalResolver principalResolver) {
        return new KmsHttpExceptionHandler(principalResolver);
    }

    /**
     * 注册 KMS 成功操作审计事件构造器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsAuditPublisher.class)
    public KmsAuditPublisher kmsAuditPublisher(KmsClock clock, KmsEventPublisher eventPublisher) {
        return new KmsAuditPublisher(clock, eventPublisher);
    }

    /**
     * 注册默认双层授权服务。
     */
    @Bean
    @ConditionalOnMissingBean(KmsAuthorizationService.class)
    public KmsAuthorizationService kmsAuthorizationService(KmsKeyLock keyLock, KmsClock clock,
                                                           KmsKeyRepository keyRepository,
                                                           KmsKeyVersionRepository keyVersionRepository,
                                                           KmsKeyPolicyRepository keyPolicyRepository) {
        return new DefaultKmsAuthorizationService(keyLock, clock, keyRepository, keyVersionRepository,
                keyPolicyRepository);
    }

    /**
     * 注册默认密码学操作服务。
     */
    @Bean
    @ConditionalOnMissingBean(CryptoOperationService.class)
    public CryptoOperationService cryptoOperationService(KmsAuthorizationService authorizationService,
                                                         KmsKeyLock keyLock, KmsKeyRepository keyRepository,
                                                         KmsKeyVersionRepository keyVersionRepository,
                                                         KmsCryptoEngine cryptoEngine,
                                                         KmsEnvelopeEncryptionEngine envelopeEncryptionEngine,
                                                         KmsAuditPublisher auditPublisher) {
        return new DefaultCryptoOperationService(authorizationService, keyLock, keyRepository, keyVersionRepository,
                cryptoEngine, envelopeEncryptionEngine, auditPublisher);
    }

    /**
     * 注册默认 REST 签名结果服务。
     */
    @Bean
    @ConditionalOnMissingBean(KmsSignatureOperationService.class)
    public KmsSignatureOperationService kmsSignatureOperationService(KmsKeyLock keyLock,
                                                                     KmsKeyRepository keyRepository,
                                                                     CryptoOperationService cryptoOperationService,
                                                                     KmsAuditPublisher auditPublisher) {
        return new DefaultKmsSignatureOperationService(keyLock, keyRepository, cryptoOperationService, auditPublisher);
    }

    /**
     * 注册默认公钥发布服务。
     */
    @Bean
    @ConditionalOnMissingBean(PublicKeyService.class)
    public PublicKeyService publicKeyService(KmsAuthorizationService authorizationService, KmsKeyLock keyLock,
                                             KmsKeyRepository keyRepository,
                                             KmsKeyVersionRepository keyVersionRepository,
                                             KmsAuditPublisher auditPublisher) {
        return new DefaultPublicKeyService(authorizationService, keyLock, keyRepository, keyVersionRepository,
                auditPublisher);
    }

    /**
     * 注册独立的人员本人公钥服务，不替换原使用策略服务。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMyPublicKeyService.class)
    public KmsMyPublicKeyService kmsMyPublicKeyService(KmsKeyLock keyLock, KmsKeyRepository keyRepository,
                                                       KmsKeyVersionRepository keyVersionRepository,
                                                       KmsAuditPublisher auditPublisher) {
        return new DefaultKmsMyPublicKeyService(keyLock, keyRepository, keyVersionRepository, auditPublisher);
    }

    /**
     * 注册角色 API 权限约束的人员本人公钥路由。
     */
    @Bean
    @ConditionalOnMissingBean(KmsMyPublicKeyController.class)
    public KmsMyPublicKeyController kmsMyPublicKeyController(KmsPrincipalResolver principalResolver,
                                                             SmartKmsServerProperties properties,
                                                             KmsMyPublicKeyService publicKeyService) {
        return new KmsMyPublicKeyController(principalResolver, properties, publicKeyService);
    }

    /**
     * 注册默认密钥策略管理服务。
     */
    @Bean
    @ConditionalOnMissingBean(KeyPolicyManagementService.class)
    public KeyPolicyManagementService keyPolicyManagementService(KmsKeyLock keyLock, KmsClock clock,
                                                                 KmsKeyRepository keyRepository,
                                                                 KmsKeyVersionRepository keyVersionRepository,
                                                                 KmsKeyPolicyRepository keyPolicyRepository,
                                                                 KmsAuditPublisher auditPublisher) {
        return new DefaultKeyPolicyManagementService(keyLock, clock, keyRepository, keyVersionRepository,
                keyPolicyRepository, auditPublisher);
    }

    /**
     * 注册默认密钥生命周期管理服务。
     */
    @Bean
    @ConditionalOnMissingBean(KeyManagementService.class)
    public KeyManagementService keyManagementService(KmsKeyLock keyLock, KmsClock clock,
                                                     KmsKeyRepository keyRepository,
                                                     KmsKeyQueryRepository keyQueryRepository,
                                                     KmsKeyVersionRepository keyVersionRepository,
                                                     KmsDestructionJobRepository destructionJobRepository,
                                                     KmsDestructionCancellationGuard destructionCancellationGuard,
                                                     KmsKeyMaterialGenerator keyMaterialGenerator,
                                                     KmsAuditPublisher auditPublisher,
                                                     KmsOwnerDestructionPolicyRepository ownerDestructionPolicyRepository) {
        return new DefaultKeyManagementService(keyLock, clock, keyRepository, keyQueryRepository,
                keyVersionRepository, destructionJobRepository, destructionCancellationGuard, keyMaterialGenerator,
                auditPublisher, ownerDestructionPolicyRepository);
    }

    /**
     * 注册 owner 级销毁窗口政策 JDBC 仓储。
     */
    @Bean
    @ConditionalOnMissingBean(KmsOwnerDestructionPolicyRepository.class)
    public JdbcKmsOwnerDestructionPolicyRepository kmsOwnerDestructionPolicyRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsOwnerDestructionPolicyRepository(jdbcTemplate);
    }

    /**
     * 注册销毁窗口政策控制器。
     */
    @Bean
    @ConditionalOnMissingBean(KmsOwnerDestructionPolicyController.class)
    public KmsOwnerDestructionPolicyController kmsOwnerDestructionPolicyController(KmsPrincipalResolver principalResolver,
                                                                                   SmartKmsServerProperties properties, KmsOwnerDestructionPolicyRepository policyRepository,
                                                                                   KmsAuditPublisher auditPublisher, KmsClock clock) {
        return new KmsOwnerDestructionPolicyController(principalResolver, properties, policyRepository,
                auditPublisher, clock);
    }

    /**
     * 注册默认销毁任务处理服务。
     */
    @Bean
    @ConditionalOnMissingBean(DestructionJobService.class)
    public DestructionJobService destructionJobService(KmsKeyLock keyLock, KmsClock clock,
                                                       KmsKeyRepository keyRepository,
                                                       KmsKeyVersionRepository keyVersionRepository,
                                                       KmsDestructionJobRepository destructionJobRepository,
                                                       KmsDestructionWorkerStateRepository workerStateRepository,
                                                       SmartKmsServerProperties properties,
                                                       PlatformTransactionManager transactionManager,
                                                       KmsAuditPublisher auditPublisher) {
        return new DefaultDestructionJobService(keyLock, clock, keyRepository, keyVersionRepository,
                destructionJobRepository, workerStateRepository, properties, transactionManager, auditPublisher);
    }

    /**
     * 注册默认销毁 worker 健康服务。
     */
    @Bean
    @ConditionalOnMissingBean(DestructionWorkerHealthService.class)
    public DestructionWorkerHealthService destructionWorkerHealthService(KmsClock clock,
                                                                         KmsDestructionJobRepository jobRepository,
                                                                         KmsDestructionWorkerStateRepository stateRepository,
                                                                         SmartKmsServerProperties properties) {
        return new DefaultDestructionWorkerHealthService(clock, jobRepository, stateRepository, properties);
    }

    /**
     * 注册默认销毁 worker 调度器。
     *
     * @return 内部销毁 worker 调度器
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "kmsDestructionTaskScheduler")
    public TaskScheduler kmsDestructionTaskScheduler() {
        return KmsDestructionWorkerLifecycle.createTaskScheduler();
    }

    /**
     * 注册内部销毁 worker 生命周期。
     */
    @Bean
    @ConditionalOnMissingBean(KmsDestructionWorkerLifecycle.class)
    public KmsDestructionWorkerLifecycle kmsDestructionWorkerLifecycle(DestructionJobService destructionJobService,
                                                                       SmartKmsServerProperties properties,
                                                                       TaskScheduler kmsDestructionTaskScheduler) {
        return new KmsDestructionWorkerLifecycle(destructionJobService, properties, kmsDestructionTaskScheduler);
    }

    /**
     * 注册默认销毁 worker 状态仓储。
     *
     * @param jdbcTemplate 执行命名参数 SQL 的 JDBC 模板
     * @return 默认销毁 worker 状态仓储
     */
    @Bean
    @ConditionalOnMissingBean(KmsDestructionWorkerStateRepository.class)
    public KmsDestructionWorkerStateRepository kmsDestructionWorkerStateRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcKmsDestructionWorkerStateRepository(jdbcTemplate);
    }
}
