package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.ConfirmQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.IssueQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.PublishOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOrderRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事务原子性探针：确认链中途失败必须整单无残留（订单/履约/版本行零写入）。
 * 探针方式：消费者注册表置空（确认链在消费者选择处必炸）→ 断言订单与履约零行。
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerTxRollbackProbeTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    @Autowired
    private SimpleCrmServerJdbcOrderRepository orderRepository;

    @Autowired
    private SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository;

    @Autowired
    private DataSource dataSource;

    private final CrmOwner owner = CrmOwner.human("tx-probe-owner");
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeAll
    void schema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
    }

    @Test
    void confirmFailureMidwayLeavesNoOrderOrFulfillmentResidue() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("事务探针客户"));
        String offeringId = commandService.publishOffering(owner, meta(),
                new PublishOfferingCommand(commandService.createOffering(owner, meta(),
                        new CreateOfferingCommand("ref-tx-probe", "探针目录",
                                CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                                "capability-tx-probe-none"))
                        .getOfferingId())).getOfferingId();
        QuotationDraft draft = commandService.createQuotation(owner, meta(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(1).toInstant(),
                        Collections.singletonList(new QuotationLineCommand(offeringId,
                                new BigDecimal("1"), "SET",
                                new Money(new BigDecimal("9.90"), "CNY"),
                                "subject-tx", "scope-tx"))));
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance issuance =
                commandService.issueQuotation(owner, meta(),
                        new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                                draft.getQuotation().getAggregateVersion()));

        long ordersBefore = count("SELECT COUNT(*) FROM crm_order");
        long itemsBefore = count("SELECT COUNT(*) FROM crm_fulfillment_item");
        Exception failure = assertThrows(Exception.class, () -> commandService.confirmQuotation(
                owner, meta(),
                new ConfirmQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        issuance.getQuotation().getAggregateVersion())));
        log.info("确认中途失败（预期：无消费者）：{}", failure.getMessage());
        long ordersAfter = count("SELECT COUNT(*) FROM crm_order");
        long itemsAfter = count("SELECT COUNT(*) FROM crm_fulfillment_item");
        log.info("订单行 {}→{}，履约行 {}→{}", ordersBefore, ordersAfter, itemsBefore, itemsAfter);
        assertEquals(ordersBefore, ordersAfter, "确认中途失败不得残留订单（事务原子性）");
        assertEquals(itemsBefore, itemsAfter, "确认中途失败不得残留履约项（事务原子性）");
    }

    private long count(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private CrmCommandMetadata meta() {
        seq.incrementAndGet();
        return new CrmCommandMetadata("tx-probe-" + seq.get(), "tx-" + seq.get());
    }
}
