package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.configuration.SimpleCrmServerAutoConfiguration;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerJdbcIdempotencyPort;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 自动配置装配测试：enable 三态、Bean 全集、自定义覆盖让位。
 *
 * <p>装配验证只提供 JDBC 模板桩，不访问数据库；SQL 方言、Route 接管和事务边界由真实 MySQL
 * 集成测试验证，避免用 H2 伪装生产数据库。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleCrmServerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleCrmServerAutoConfiguration.class));

    @Test
    void enableFalseDoesNotRegisterAnyBean() {
        runner.run(context -> {
            log.info("enable 缺省时 SimpleCrmServerCommandService 存在={}", context.containsBean("crmCommandService"));
            assertEquals(false, context.containsBean("crmCommandService"), "默认关闭不得注册任何 Bean");
        });
    }

    @Test
    void enableTrueRegistersFullChainWithJdbcTemplate() {
        runner.withPropertyValues("io.github.surezzzzzz.sdk.crm.server.enable=true")
                .withUserConfiguration(JdbcTemplateConfiguration.class)
                .run(context -> {
                    assertNotNull(context.getBean(SimpleCrmServerCommandService.class), "命令服务应装配");
                    assertNotNull(context.getBean(SimpleCrmServerQueryService.class), "查询服务应装配");
                    assertNotNull(context.getBean(SimpleCrmServerJdbcIdempotencyPort.class), "幂等 Port 应装配");
                    assertNotNull(context.getBean(
                            io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerExceptionHandler.class),
                            "领域异常映射应装配（窄扫描下 advice 必须带组件注解，否则领域错误全部裸 500）");
                    log.info("全链装配结果：Bean 数={}", context.getBeansOfType(Object.class).size());
                });
    }

    @Test
    void missingJdbcTemplateFailsStartup() {
        runner.withPropertyValues("io.github.surezzzzzz.sdk.crm.server.enable=true")
                .run(context -> {
                    log.info("缺宿主 JDBC 模板启动失败：{}", context.getStartupFailure() == null ? "无"
                            : context.getStartupFailure().getClass().getSimpleName());
                    assertEquals(true, context.getStartupFailure() != null,
                            "缺少宿主 JDBC 模板必须启动失败（无匿名回退）");
                });
    }

    @Test
    void customCommandServiceOverridesDefault() {
        runner.withPropertyValues("io.github.surezzzzzz.sdk.crm.server.enable=true")
                .withUserConfiguration(JdbcTemplateConfiguration.class, OverrideConfiguration.class)
                .run(context -> {
                    SimpleCrmServerCommandService service = context.getBean(SimpleCrmServerCommandService.class);
                    log.info("自定义命令服务覆盖：{}", service.getClass().getName());
                    assertEquals(true, service.getClass().getName().contains("StubSimpleCrmServerCommandService"),
                            "宿主自定义命令服务必须让位默认实现");
                });
    }

    /**
     * 装配测试 JDBC 模板（只验证 Bean 图，不访问数据库）。
     *
     * @author surezzzzzz
     */
    @Configuration
    static class JdbcTemplateConfiguration {

        /**
         * JDBC 模板桩。
         *
         * @return JDBC 模板
         */
        @Bean
        public NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return Mockito.mock(NamedParameterJdbcTemplate.class);
        }
    }

    /**
     * 自定义覆盖配置。
     *
     * @author surezzzzzz
     */
    @Configuration
    static class OverrideConfiguration {

        /**
         * 桩命令服务。
         *
         * @return 桩
         */
        @Bean
        public SimpleCrmServerCommandService stubCommandService() {
            return new StubSimpleCrmServerCommandService();
        }
    }

    /**
     * 桩命令服务（仅验证覆盖让位，不承载业务）。
     *
     * @author surezzzzzz
     */
    static class StubSimpleCrmServerCommandService extends SimpleCrmServerCommandService {

        StubSimpleCrmServerCommandService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
