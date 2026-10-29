package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 复现线上 400：创建商机携带 estimatedAmount+currency 是否被拒（走查环境实测同 body 必 400）。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
class SimpleCrmServerOpportunityAmountReproTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    private static final CrmOwner OWNER = CrmOwner.human("iam:1045904984437075");

    @Test
    void createOpportunityWithAmountAndCurrencySucceeds() {
        Customer customer = commandService.createCustomer(OWNER, metadata(),
                new CreateCustomerCommand("金额商机复现客户"));
        Opportunity opportunity = commandService.createOpportunity(OWNER, metadata(),
                new CreateOpportunityCommand(customer.getCustomerId(), "金额商机",
                        null, null, LocalDate.parse("2026-12-31"),
                        new BigDecimal("88000"), "CNY"));
        assertEquals("new", opportunity.getStage().getCode(),
                "携带 estimatedAmount+currency 创建商机必须成功（线上 400 是缺陷）");
    }

    private CrmCommandMetadata metadata() {
        return new CrmCommandMetadata(UUID.randomUUID().toString(), "repro-" + UUID.randomUUID());
    }
}
