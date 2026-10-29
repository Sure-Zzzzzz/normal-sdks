package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.configuration.SimpleCrmServerAutoConfiguration;
import io.github.surezzzzzz.sdk.crm.server.core.port.audit.CrmAuditPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmClock;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcAuditRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerJdbcIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * CRM 组件扫描与可替换默认实现的装配契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleCrmServerBeanAssemblyContractTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleCrmServerAutoConfiguration.class))
            .withPropertyValues("io.github.surezzzzzz.sdk.crm.server.enable=true")
            .withUserConfiguration(JdbcTemplateConfiguration.class);

    @Test
    void customScanRegistersRepositoriesAndAuditDelegate() {
        runner.run(context -> {
            SimpleCrmServerJdbcAuditRepository repository =
                    context.getBean(SimpleCrmServerJdbcAuditRepository.class);
            CrmAuditPort primary = context.getBean(CrmAuditPort.class);
            log.info("审计 Bean：底层={}, 主端口={}", repository.getClass().getName(), primary.getClass().getName());
            assertSame(repository, context.getBean(SimpleCrmServerJdbcAuditRepository.class), "审计仓储应由自定义注解扫描注册");
            assertTrue(primary != repository, "审计装饰器不能把自身注入为委托");
            assertTrue(context.getBeansOfType(io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcCustomerRepository.class).size() == 1,
                    "客户仓储应由自定义注解扫描注册");
            assertTrue(context.getBeansOfType(SimpleCrmServerJdbcIdempotencyPort.class).size() == 1,
                    "幂等端口应由自定义注解扫描注册");
        });
    }

    @Test
    void hostPortsReplaceDefaults() {
        CrmClock clock = () -> Instant.parse("2026-01-01T00:00:00Z");
        CrmIdGenerator idGenerator = resourceType -> "host-id";
        SimpleCrmServerOwnerResolverBridge ownerBridge = new SimpleCrmServerOwnerResolverBridge();
        runner.withBean(CrmClock.class, () -> clock)
                .withBean(CrmIdGenerator.class, () -> idGenerator)
                .withBean(SimpleCrmServerOwnerResolverBridge.class, () -> ownerBridge)
                .run(context -> {
                    log.info("宿主覆盖端口：clock={}, idGenerator={}, ownerBridge={}",
                            context.getBean(CrmClock.class).getClass().getName(),
                            context.getBean(CrmIdGenerator.class).getClass().getName(),
                            context.getBean(SimpleCrmServerOwnerResolverBridge.class).getClass().getName());
                    assertSame(clock, context.getBean(CrmClock.class), "宿主时钟必须覆盖默认实现");
                    assertSame(idGenerator, context.getBean(CrmIdGenerator.class), "宿主 ID 生成器必须覆盖默认实现");
                    assertSame(ownerBridge, context.getBean(SimpleCrmServerOwnerResolverBridge.class),
                            "宿主 owner 桥必须覆盖默认实现");
                });
    }

    /**
     * 装配测试使用的 JDBC 模板桩，不访问数据库。
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
        NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
            return mock(NamedParameterJdbcTemplate.class);
        }
    }

}
