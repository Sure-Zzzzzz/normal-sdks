package io.github.surezzzzzz.sdk.crm.server.configuration;

import io.github.surezzzzzz.sdk.crm.server.core.port.audit.CrmAuditPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmCreateIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmReplayableIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmClock;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.SimpleCrmServerPackage;
import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcAuditRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcContactRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcCustomerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOfferingRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOpportunityRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOrderRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcQuotationRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerClockAdapter;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerIdGeneratorAdapter;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple CRM Server 自动配置。
 *
 * <p>总开关默认关闭，必须显式开启。仓储直接消费 Spring Boot 标准装配的
 * NamedParameterJdbcTemplate（KMS 同构）：生产宿主由传递的 mysql-route
 * 接管数据源与事务；测试或明确的宿主覆盖可以提供同型模板。模板缺失即启动失败，无匿名回退。
 * 默认服务和系统端口在本类中直接声明，保留稳定的宿主覆盖边界。</p>
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = SimpleCrmServerConstant.CONFIG_PREFIX,
        name = SimpleCrmServerConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleCrmServerConstant.BOOLEAN_TRUE)
@ComponentScan(basePackageClasses = SimpleCrmServerPackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleCrmServerComponent.class))
public class SimpleCrmServerAutoConfiguration {

    /**
     * 注册默认命令编排服务，宿主提供同类型服务时让位。
     *
     * @return 命令编排服务
     */
    @Bean("crmCommandService")
    @ConditionalOnMissingBean(SimpleCrmServerCommandService.class)
    public SimpleCrmServerCommandService crmCommandService(
            SimpleCrmServerJdbcCustomerRepository customerRepository,
            SimpleCrmServerJdbcContactRepository contactRepository,
            SimpleCrmServerJdbcOfferingRepository offeringRepository,
            SimpleCrmServerJdbcQuotationRepository quotationRepository,
            SimpleCrmServerJdbcOpportunityRepository opportunityRepository,
            SimpleCrmServerJdbcOrderRepository orderRepository,
            SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository,
            SimpleCrmServerJdbcFulfillmentConsumerRepository consumerRepository,
            CrmCreateIdempotencyPort createIdempotency,
            CrmReplayableIdempotencyPort replayableIdempotency,
            CrmAuditPort auditPort,
            CrmClock clock,
            CrmIdGenerator idGenerator) {
        return new SimpleCrmServerCommandService(customerRepository, contactRepository, offeringRepository,
                quotationRepository, opportunityRepository, orderRepository, fulfillmentItemRepository, consumerRepository,
                createIdempotency, replayableIdempotency, auditPort, clock, idGenerator);
    }

    /**
     * 注册默认查询编排服务，宿主提供同类型服务时让位。
     *
     * @return 查询编排服务
     */
    @Bean("crmQueryService")
    @ConditionalOnMissingBean(SimpleCrmServerQueryService.class)
    public SimpleCrmServerQueryService crmQueryService(
            SimpleCrmServerJdbcCustomerRepository customerRepository,
            SimpleCrmServerJdbcContactRepository contactRepository,
            SimpleCrmServerJdbcOfferingRepository offeringRepository,
            SimpleCrmServerJdbcOpportunityRepository opportunityRepository,
            SimpleCrmServerJdbcQuotationRepository quotationRepository,
            SimpleCrmServerJdbcOrderRepository orderRepository,
            SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository,
            SimpleCrmServerJdbcAuditRepository auditRepository,
            CrmClock clock) {
        return new SimpleCrmServerQueryService(customerRepository, contactRepository, offeringRepository,
                opportunityRepository, quotationRepository, orderRepository, fulfillmentItemRepository, auditRepository, clock);
    }

    /**
     * 注册默认系统时钟，宿主提供同类型时钟时让位。
     *
     * @return 系统时钟
     */
    @Bean("crmClockAdapter")
    @ConditionalOnMissingBean(CrmClock.class)
    public CrmClock crmClockAdapter() {
        return new SimpleCrmServerClockAdapter();
    }

    /**
     * 注册默认资源 ID 生成器，宿主提供同类型生成器时让位。
     *
     * @return ID 生成器
     */
    @Bean("crmIdGeneratorAdapter")
    @ConditionalOnMissingBean(CrmIdGenerator.class)
    public CrmIdGenerator crmIdGeneratorAdapter() {
        return new SimpleCrmServerIdGeneratorAdapter();
    }

    /**
     * 注册散形态 owner 解析桥；Resource 协作配置会在本配置前提供更具体的桥。
     *
     * @return owner 解析桥
     */
    @Bean("crmOwnerResolverBridge")
    @ConditionalOnMissingBean(SimpleCrmServerOwnerResolverBridge.class)
    public SimpleCrmServerOwnerResolverBridge crmOwnerResolverBridge() {
        return new SimpleCrmServerOwnerResolverBridge();
    }
}
