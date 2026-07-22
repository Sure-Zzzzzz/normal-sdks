package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementApiSecurityConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementAutoConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementSecurityConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementRestSecurityConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.SmartRedisLimiterManagementPageController;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.SmartRedisLimiterPolicyAdminController;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.SmartRedisLimiterPolicyController;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.SmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementConfigurationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.JdbcSmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicySnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Management 自动配置契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterManagementAutoConfigurationTest {

    private static final String PREFIX = SmartRedisLimiterManagementConstant.CONFIG_PREFIX;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmartRedisLimiterManagementAutoConfiguration.class));

    @Test
    public void testDisabledCreatesNoManagementBeans() {
        contextRunner.run(context -> {
            assertTrue(context.getStartupFailure() == null, "默认关闭时上下文必须正常启动");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementProperties.class).size(),
                    "management 关闭时不得注册配置 Bean");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyRepository.class).size(),
                    "management 关闭时不得注册 Repository");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyManagementService.class).size(),
                    "management 关闭时不得注册管理服务");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicySnapshotService.class).size(),
                    "management 关闭时不得注册快照服务");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyController.class).size(),
                    "management 关闭时不得暴露对外策略接口");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyAdminController.class).size(),
                    "management 关闭时不得暴露管理接口");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementPageController.class).size(),
                    "management 关闭时不得暴露管理页面");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementSecurityConfiguration.class).size(),
                    "management 关闭时不得注册 UI 安全链");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementApiSecurityConfiguration.class).size(),
                    "management 关闭时不得注册 API 安全链");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementRestSecurityConfiguration.class).size(),
                    "management 关闭时不得注册对外 REST 固定 token 安全链");
        });
    }

    @Test
    public void testApiOnlyCreatesSnapshotEndpointWithoutUiOrSecurityChain() {
        contextRunner
                .withUserConfiguration(UserExtensionConfiguration.class)
                .withPropertyValues(PREFIX + ".enable=true", PREFIX + ".api.enable=true",
                        SmartRedisLimiterManagementConstant.RESOURCE_SERVER_CONFIG_PREFIX + ".enabled=true")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null, "仅启用 API 时上下文必须正常启动");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyController.class).size(),
                            "仅启用 API 时必须暴露对外策略接口");
                    assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyAdminController.class).size(),
                            "仅启用 API 时不得暴露管理接口");
                    assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementPageController.class).size(),
                            "仅启用 API 时不得暴露管理页面");
                    assertEquals(0, context.getBeansOfType(SmartRedisLimiterManagementSecurityConfiguration.class).size(),
                            "仅启用 API 时不得注册 UI 安全链");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterManagementApiSecurityConfiguration.class).size(),
                            "仅启用 API 时必须注册 API 安全链");
                    SmartRedisLimiterManagementProperties properties =
                            context.getBean(SmartRedisLimiterManagementProperties.class);
                    assertEquals(SmartRedisLimiterManagementConstant.DEFAULT_API_BASE_PATH,
                            properties.getApi().getBasePath(), "API 必须使用独立默认根路径");
                    assertUserExtensions(context);
                });
    }

    @Test
    public void testUiRequiresApiToKeepPageAndManagementApiSameOrigin() {
        assertConfigurationFailure(
                PREFIX + ".enable=true",
                PREFIX + ".ui.enable=true",
                PREFIX + ".admin.username=management-admin",
                PREFIX + ".admin.password=management-password",
                "仅启用 UI 时必须拒绝启动");
    }

    @Test
    public void testApiAndUiCreateIndependentEndpointsAndSecurityChains() {
        contextRunner
                .withUserConfiguration(UserExtensionConfiguration.class)
                .withPropertyValues(
                        PREFIX + ".enable=true",
                        PREFIX + ".api.enable=true",
                        PREFIX + ".api.base-path=/internal/limiter-api",
                        PREFIX + ".ui.enable=true",
                        PREFIX + ".ui.base-path=/ops/limiter-ui",
                        PREFIX + ".admin.username=management-admin",
                        PREFIX + ".admin.password=management-password",
                        SmartRedisLimiterManagementConstant.RESOURCE_SERVER_CONFIG_PREFIX + ".enabled=true")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null, "API 与 UI 同时开启时上下文必须正常启动");
                    SmartRedisLimiterManagementProperties properties =
                            context.getBean(SmartRedisLimiterManagementProperties.class);
                    assertEquals("/internal/limiter-api", properties.getApi().getBasePath(),
                            "API 必须绑定独立根路径");
                    assertEquals("/ops/limiter-ui", properties.getUi().getBasePath(),
                            "UI 必须绑定独立根路径");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyController.class).size(),
                            "启用 API 时必须暴露对外策略接口");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyAdminController.class).size(),
                            "启用 UI 时必须暴露页面管理接口");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterManagementPageController.class).size(),
                            "启用 UI 时必须暴露管理页面");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterManagementSecurityConfiguration.class).size(),
                            "启用 UI 时必须注册 UI 安全链");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterManagementApiSecurityConfiguration.class).size(),
                            "启用 API 时必须注册 API 安全链");
                    assertUserExtensions(context);
                });
    }

    @Test
    public void testDefaultRepositoryDoesNotAccessDataSourceOrExecuteDdlAtStartup() {
        contextRunner
                .withUserConfiguration(IdleDataSourceConfiguration.class)
                .withPropertyValues(PREFIX + ".enable=true", PREFIX + ".api.enable=true",
                        SmartRedisLimiterManagementConstant.RESOURCE_SERVER_CONFIG_PREFIX + ".enabled=true")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null, "默认 JDBC Repository 的装配不得要求启动即连接数据库");
                    assertEquals(1, context.getBeansOfType(JdbcSmartRedisLimiterPolicyRepository.class).size(),
                            "未提供自定义 Repository 时必须创建默认 JDBC Repository");
                    DataSource dataSource = context.getBean(DataSource.class);
                    verifyNoInteractions(dataSource);
                });
    }

    @Test
    public void testRestTokenRequiredWhenResourceServerExplicitlyDisabled() {
        contextRunner
                .withUserConfiguration(UserExtensionConfiguration.class)
                .withPropertyValues(PREFIX + ".enable=true", PREFIX + ".api.enable=true",
                        SmartRedisLimiterManagementConstant.RESOURCE_SERVER_CONFIG_PREFIX + ".enabled=false")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    SmartRedisLimiterManagementConfigurationException exception = findCause(
                            startupFailure, SmartRedisLimiterManagementConfigurationException.class);
                    log.info("REST 凭据缺失校验结果: failure={}, errorCode={}",
                            startupFailure == null ? null : startupFailure.getMessage(),
                            exception == null ? null : exception.getErrorCode());
                    assertTrue(startupFailure != null, "resource-server 显式关闭且未配 REST 凭据时必须启动失败");
                    assertTrue(exception != null, "启动失败必须包含 management 配置异常，实际=" + startupFailure);
                    assertEquals(ErrorCode.CONFIG_REST_TOKEN_REQUIRED, exception.getErrorCode(),
                            "REST 固定 token 缺失必须使用稳定错误码");
                });
    }

    @Test
    public void testInvalidManagementConfigurationsFailWithStableConfigurationError() {
        assertConfigurationFailure(
                PREFIX + ".enable=true",
                "根开关开启但 API 与 UI 都关闭时必须拒绝启动");
        assertConfigurationFailure(
                PREFIX + ".enable=true",
                PREFIX + ".api.enable=true",
                PREFIX + ".api.base-path=management",
                "非法 API 根路径必须拒绝启动");
        assertConfigurationFailure(
                PREFIX + ".enable=true",
                PREFIX + ".api.enable=true",
                PREFIX + ".ui.enable=true",
                PREFIX + ".admin.username=management-admin",
                PREFIX + ".admin.password=management-password",
                PREFIX + ".api.base-path=/management",
                PREFIX + ".ui.base-path=/management/ui",
                "重叠 API/UI 根路径必须拒绝启动");
        assertConfigurationFailure(
                PREFIX + ".enable=true",
                PREFIX + ".api.enable=true",
                PREFIX + ".ui.enable=true",
                "UI 开启但管理员凭据缺失时必须拒绝启动");
        assertConfigurationFailure(
                PREFIX + ".enable=true",
                PREFIX + ".api.enable=true",
                PREFIX + ".page.default-size=101",
                PREFIX + ".page.max-size=100",
                "非法分页范围必须拒绝启动");
    }

    private void assertConfigurationFailure(String... properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            Throwable startupFailure = context.getStartupFailure();
            SmartRedisLimiterManagementConfigurationException exception = findCause(
                    startupFailure, SmartRedisLimiterManagementConfigurationException.class);
            log.info("management 配置校验结果: failure={}, errorCode={}",
                    startupFailure == null ? null : startupFailure.getMessage(),
                    exception == null ? null : exception.getErrorCode());
            assertTrue(startupFailure != null, "配置不合法时必须启动失败");
            assertTrue(exception != null, "启动失败必须包含 management 配置异常，实际=" + startupFailure);
            assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED, exception.getErrorCode(),
                    "配置错误必须使用稳定错误码");
        });
    }

    private void assertUserExtensions(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyRepository.class).size(),
                "用户 Repository 存在时默认 Repository 必须让位");
        assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyManagementService.class).size(),
                "用户管理服务存在时默认管理服务必须让位");
        assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicySnapshotService.class).size(),
                "用户快照服务存在时默认快照服务必须让位");
        assertEquals(1, context.getBeansOfType(SmartRedisLimiterManagementOperatorProvider.class).size(),
                "用户操作人 Provider 存在时默认 Provider 必须让位");
        assertEquals(1, context.getBeansOfType(SmartRedisLimiterManagementEventPublisher.class).size(),
                "用户事件发布器存在时默认发布器必须让位");
        assertSame(context.getBean(UserExtensions.class).repository,
                context.getBean(SmartRedisLimiterPolicyRepository.class), "必须注入用户 Repository");
        assertSame(context.getBean(UserExtensions.class).managementService,
                context.getBean(SmartRedisLimiterPolicyManagementService.class), "必须注入用户管理服务");
        assertSame(context.getBean(UserExtensions.class).snapshotService,
                context.getBean(SmartRedisLimiterPolicySnapshotService.class), "必须注入用户快照服务");
        assertSame(context.getBean(UserExtensions.class).operatorProvider,
                context.getBean(SmartRedisLimiterManagementOperatorProvider.class), "必须注入用户操作人 Provider");
        assertSame(context.getBean(UserExtensions.class).eventPublisher,
                context.getBean(SmartRedisLimiterManagementEventPublisher.class), "必须注入用户事件发布器");
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    @TestConfiguration
    public static class UserExtensionConfiguration {

        @Bean
        public UserExtensions userExtensions() {
            return new UserExtensions();
        }

        @Bean
        public SmartRedisLimiterPolicyRepository smartRedisLimiterPolicyRepository(UserExtensions extensions) {
            return extensions.repository;
        }

        @Bean
        public SmartRedisLimiterPolicyManagementService smartRedisLimiterPolicyManagementService(
                UserExtensions extensions) {
            return extensions.managementService;
        }

        @Bean
        public SmartRedisLimiterPolicySnapshotService smartRedisLimiterPolicySnapshotService(
                UserExtensions extensions) {
            return extensions.snapshotService;
        }

        @Bean
        public SmartRedisLimiterManagementOperatorProvider smartRedisLimiterManagementOperatorProvider(
                UserExtensions extensions) {
            return extensions.operatorProvider;
        }

        @Bean
        public SmartRedisLimiterManagementEventPublisher smartRedisLimiterManagementEventPublisher(
                UserExtensions extensions) {
            return extensions.eventPublisher;
        }
    }

    @TestConfiguration
    public static class IdleDataSourceConfiguration {

        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }
    }

    public static class UserExtensions {
        private final SmartRedisLimiterPolicyRepository repository = mock(SmartRedisLimiterPolicyRepository.class);
        private final SmartRedisLimiterPolicyManagementService managementService =
                mock(SmartRedisLimiterPolicyManagementService.class);
        private final SmartRedisLimiterPolicySnapshotService snapshotService =
                mock(SmartRedisLimiterPolicySnapshotService.class);
        private final SmartRedisLimiterManagementOperatorProvider operatorProvider =
                mock(SmartRedisLimiterManagementOperatorProvider.class);
        private final SmartRedisLimiterManagementEventPublisher eventPublisher =
                mock(SmartRedisLimiterManagementEventPublisher.class);
    }
}
