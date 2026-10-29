package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerQuotationController;
import io.github.surezzzzzz.sdk.crm.server.core.command.ActivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ConfirmQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateContactCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.DeactivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.DisableOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.EditQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.IssueQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.MergeCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.PublishOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.RenegotiateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ReportFulfillmentResultCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ResetFulfillmentItemCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.WithdrawQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerMergeResult;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerTransferResult;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOrderRepository;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerQuotationTransferResult;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerJsonCodec;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 MySQL 幂等回归：后续迁移不覆盖首次响应，重放不改变当前事实、订单或审计。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerIdempotentReplayProbeTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    @Autowired
    private SimpleCrmServerQueryService queryService;

    @Autowired
    private SimpleCrmServerJdbcFulfillmentConsumerRepository consumerRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SimpleCrmServerJdbcFulfillmentItemRepository items;

    @Autowired
    private SimpleCrmServerJdbcOrderRepository orders;

    private final CrmOwner owner = CrmOwner.human("replay-probe-owner");
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeAll
    void schema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
        consumerRepository.upsert(new FulfillmentConsumer("consumer-replay", "capability-replay",
                1, true));
    }

    @Test
    void issuingTwiceWithSameKeyReplaysSameIssuance() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("重放探针客户I"));
        String offeringId = publish("ref-replay-i", "capability-replay");
        QuotationDraft draft = quote(customer, offeringId);
        CrmCommandMetadata key = meta();
        QuotationIssuance first = commandService.issueQuotation(owner, key,
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion()));
        QuotationIssuance replay = commandService.issueQuotation(owner, key,
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion()));
        log.info("签发重放：首版={}，重放版={}", first.getQuotationVersion().getVersion(),
                replay.getQuotationVersion().getVersion());
        assertEquals(first.getQuotationVersion().getState(), replay.getQuotationVersion().getState(),
                "同键重放必须重建同一签发事实（而非二次迁移被拒）");
    }

    @Test
    void confirmingTwiceWithSameKeyReplaysSameOrder() {
        consumerRepository.upsert(new FulfillmentConsumer("consumer-replay", "capability-replay",
                1, true));
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("重放探针客户C"));
        String offeringId = publish("ref-replay-c", "capability-replay");
        QuotationDraft draft = quote(customer, offeringId);
        QuotationIssuance issuance = commandService.issueQuotation(owner, meta(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion()));
        CrmCommandMetadata key = meta();
        QuotationConfirmation first = commandService.confirmQuotation(owner, key,
                new ConfirmQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        issuance.getQuotation().getAggregateVersion()));
        QuotationConfirmation replay = commandService.confirmQuotation(owner, key,
                new ConfirmQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        issuance.getQuotation().getAggregateVersion()));
        log.info("确认重放：首单={}，重放单={}，履约={}", first.getOrder().getOrderId(),
                replay.getOrder().getOrderId(), replay.getFulfillmentItems().size());
        assertEquals(first.getOrder().getOrderId(), replay.getOrder().getOrderId(),
                "同键确认重放必须重建同一订单");
        assertEquals(first.getFulfillmentItems().size(), replay.getFulfillmentItems().size());
        assertNotNull(replay.getOrder().getOrderId());
    }

    @Test
    void claimReplayAfterCompletionKeepsFirstResponseAndCurrentTerminalFacts() {
        QuotationConfirmation confirmation = confirmed("claim");
        FulfillmentItem item = confirmation.getFulfillmentItems().get(0);
        CrmOwner reporter = CrmOwner.service(item.getConsumerId());
        CrmCommandMetadata key = meta();
        ReportFulfillmentResultCommand claim = new ReportFulfillmentResultCommand(
                item.getFulfillmentId(), 1, "in_progress", null, "领取交付");
        FulfillmentItem first = commandService.reportFulfillmentResult(reporter, key, claim);
        FulfillmentItem terminal = commandService.reportFulfillmentResult(reporter, meta(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 2, "completed",
                        "{\"receipt\":\"accepted\"}", "完成交付"));
        String orderBefore = SimpleCrmServerJsonCodec.write(orders.findById(item.getOrderId()).get());
        long auditBefore = auditCount(item.getFulfillmentId());
        assertSameResult(first, commandService.reportFulfillmentResult(reporter, key, claim));
        assertSameResult(terminal, items.findById(item.getFulfillmentId()).get());
        assertEquals(orderBefore, SimpleCrmServerJsonCodec.write(orders.findById(item.getOrderId()).get()));
        assertEquals(auditBefore, auditCount(item.getFulfillmentId()));
        assertEquals(2L, auditBefore);
    }

    @Test
    void failureAndResetReplayAfterRecoveryPreserveBothOriginalResponses() {
        FulfillmentItem item = confirmed("reset").getFulfillmentItems().get(0);
        CrmOwner reporter = CrmOwner.service(item.getConsumerId());
        CrmCommandMetadata failKey = meta();
        ReportFulfillmentResultCommand fail = new ReportFulfillmentResultCommand(
                item.getFulfillmentId(), 1, "failed", "{\"failure\":\"temporary\"}", null);
        FulfillmentItem failed = commandService.reportFulfillmentResult(reporter, failKey, fail);
        CrmCommandMetadata resetKey = meta();
        ResetFulfillmentItemCommand reset = new ResetFulfillmentItemCommand(item.getFulfillmentId());
        FulfillmentItem pending = commandService.resetFulfillmentItem(owner, resetKey, reset);
        FulfillmentItem recovered = commandService.reportFulfillmentResult(reporter, meta(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 2, "in_progress", null, null));
        assertSameResult(failed, commandService.reportFulfillmentResult(reporter, failKey, fail));
        assertSameResult(pending, commandService.resetFulfillmentItem(owner, resetKey, reset));
        assertSameResult(recovered, items.findById(item.getFulfillmentId()).get());
        assertEquals(3L, auditCount(item.getFulfillmentId()));
    }

    @Test
    void quotationValidityUsesPersistedMillisecondPrecisionOnCreateEditAndReplay() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("时间精度客户"));
        String offeringId = publish("ref-replay-time", "capability-replay");
        QuotationLineCommand line = new QuotationLineCommand(offeringId, BigDecimal.ONE, "SET",
                new Money(BigDecimal.TEN, "CNY"), "subject-time", "scope-time");
        Instant createUntil = Instant.parse("2030-01-01T00:00:00.123456789Z");
        QuotationDraft created = commandService.createQuotation(owner, meta(),
                new CreateQuotationCommand(customer.getCustomerId(), createUntil,
                        Collections.singletonList(line)));
        assertEquals(Instant.parse("2030-01-01T00:00:00.123Z"),
                created.getQuotationVersion().getValidUntil());

        Instant editUntil = Instant.parse("2030-01-02T00:00:00.987654321Z");
        EditQuotationCommand edit = new EditQuotationCommand(created.getQuotation().getQuotationId(),
                editUntil, Collections.singletonList(line));
        CrmCommandMetadata key = meta();
        QuotationDraft first = commandService.editQuotationDraft(owner, key, edit);
        assertEquals(Instant.parse("2030-01-02T00:00:00.987Z"),
                first.getQuotationVersion().getValidUntil());
        assertEquals(first.getQuotationVersion().getValidUntil(),
                queryService.findQuotationVersion(owner, null, created.getQuotation().getQuotationId(), 1)
                        .getValidUntil());
        assertSameResult(first, commandService.editQuotationDraft(owner, key, edit));
    }

    @Test
    void customerAndOfferingReplayDoNotReturnLaterStates() {
        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放状态客户"));
        CrmCommandMetadata inactiveKey = meta();
        DeactivateCustomerCommand deactivate = new DeactivateCustomerCommand(customer.getCustomerId());
        Customer inactive = commandService.deactivateCustomer(owner, inactiveKey, deactivate);
        commandService.activateCustomer(owner, meta(), new ActivateCustomerCommand(customer.getCustomerId()));
        assertSameResult(inactive, commandService.deactivateCustomer(owner, inactiveKey, deactivate));
        String offeringId = commandService.createOffering(owner, meta(), new CreateOfferingCommand(
                "ref-replay-state", "重放状态商品", CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                "capability-replay")).getOfferingId();
        CrmCommandMetadata publishKey = meta();
        PublishOfferingCommand publish = new PublishOfferingCommand(offeringId);
        Offering published = commandService.publishOffering(owner, publishKey, publish);
        commandService.disableOffering(owner, meta(), new DisableOfferingCommand(offeringId));
        assertSameResult(published, commandService.publishOffering(owner, publishKey, publish));
        assertEquals(3L, auditCount(customer.getCustomerId()));
        assertEquals(3L, auditCount(offeringId));
    }

    @Test
    void contactReplayAfterCustomerDeactivationReturnsFirstResource() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("联系人幂等客户"));
        CrmCommandMetadata key = meta();
        CreateContactCommand command = new CreateContactCommand(customer.getCustomerId(), "联系人", "负责人");
        io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact first =
                commandService.createContact(owner, key, command);
        commandService.deactivateCustomer(owner, meta(), new DeactivateCustomerCommand(customer.getCustomerId()));

        assertSameResult(first, commandService.createContact(owner, key, command));
    }

    @Test
    void opportunityReplayAfterCustomerDeactivationAndOfferingDisableReturnsFirstResource() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("商机幂等客户"));
        String offeringId = publish("ref-replay-opportunity", "capability-replay");
        CrmCommandMetadata key = meta();
        CreateOpportunityCommand command = new CreateOpportunityCommand(customer.getCustomerId(),
                "续约商机", offeringId, null, LocalDate.now().plusDays(30), null, null);
        Opportunity first = commandService.createOpportunity(owner, key, command);
        commandService.disableOffering(owner, meta(), new DisableOfferingCommand(offeringId));
        commandService.deactivateCustomer(owner, meta(), new DeactivateCustomerCommand(customer.getCustomerId()));

        assertSameResult(first, commandService.createOpportunity(owner, key, command));
    }

    @Test
    void quotationReplayAfterLinkedOpportunityBecomesTerminalReturnsFirstResource() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("报价幂等商机客户"));
        String offeringId = publish("ref-replay-quotation-opportunity", "capability-replay");
        Opportunity opportunity = commandService.createOpportunity(owner, meta(), new CreateOpportunityCommand(
                customer.getCustomerId(), "终态商机报价", offeringId, null,
                LocalDate.now().plusDays(30), null, null));
        QuotationLineCommand line = new QuotationLineCommand(offeringId, BigDecimal.ONE, "SET",
                new Money(BigDecimal.TEN, "CNY"), "subject-opportunity", "scope-opportunity");
        CreateQuotationCommand command = new CreateQuotationCommand(customer.getCustomerId(),
                opportunity.getOpportunityId(), Instant.now().plusSeconds(86400),
                Collections.singletonList(line));
        CrmCommandMetadata key = meta();
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation first =
                commandService.createQuotationResource(owner, key, command);
        QuotationIssuance issuance = commandService.issueQuotation(owner, meta(),
                new IssueQuotationCommand(first.getQuotationId(), 1, first.getAggregateVersion()));
        commandService.confirmQuotation(owner, meta(), new ConfirmQuotationCommand(first.getQuotationId(), 1,
                issuance.getQuotation().getAggregateVersion()));

        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation replay =
                commandService.createQuotationResource(owner, key, command);
        assertEquals(first.getQuotationId(), replay.getQuotationId(),
                "关联商机进入终态后，报价创建重放仍须返回首资源");
    }

    @Test
    void renegotiationReplayAfterCustomerDeactivationReturnsFirstResource() {
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("重议幂等客户"));
        QuotationDraft draft = quote(customer, publish("ref-replay-renegotiate", "capability-replay"));
        commandService.issueQuotation(owner, meta(), new IssueQuotationCommand(
                draft.getQuotation().getQuotationId(), 1, draft.getQuotation().getAggregateVersion()));
        CrmCommandMetadata key = meta();
        RenegotiateQuotationCommand command = new RenegotiateQuotationCommand(
                draft.getQuotation().getQuotationId());
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation first =
                commandService.renegotiateQuotationResource(owner, key, command);
        commandService.deactivateCustomer(owner, meta(), new DeactivateCustomerCommand(customer.getCustomerId()));

        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation replay =
                commandService.renegotiateQuotationResource(owner, key, command);
        assertEquals(first.getQuotationId(), replay.getQuotationId(),
                "客户停用后，重议重放仍须返回首派生报价");
    }

    @Test
    void mergeReplayAfterTargetIsMergedAgainReturnsFirstResult() {
        Customer source = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("合并源"));
        Customer target = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("合并目标"));
        Customer finalTarget = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("最终目标"));
        CrmCommandMetadata key = meta();
        MergeCustomerCommand firstCommand = new MergeCustomerCommand(source.getCustomerId(), target.getCustomerId());
        CustomerMergeResult first = commandService.mergeCustomer(owner, key, firstCommand);
        commandService.mergeCustomer(owner, meta(), new MergeCustomerCommand(target.getCustomerId(),
                finalTarget.getCustomerId()));

        CustomerMergeResult replay = commandService.mergeCustomer(owner, key, firstCommand);
        assertEquals(first.getMergedSourceCustomer().getCustomerId(),
                replay.getMergedSourceCustomer().getCustomerId(),
                "目标再次合并后，原合并重放仍须返回首源客户");
        assertEquals(first.getTargetCustomer().getCustomerId(), replay.getTargetCustomer().getCustomerId(),
                "目标再次合并后，原合并重放仍须返回首目标客户");
    }

    @Test
    void issuanceAndConfirmationReplayKeepSnapshotsAfterDeliveryAndCustomerDeactivation() {
        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放快照客户"));
        QuotationDraft draft = quote(customer, publish("ref-replay-snapshot", "capability-replay"));
        CrmCommandMetadata issueKey = meta();
        IssueQuotationCommand issue = new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                draft.getQuotation().getAggregateVersion());
        QuotationIssuance issued = commandService.issueQuotation(owner, issueKey, issue);
        CrmCommandMetadata confirmKey = meta();
        ConfirmQuotationCommand confirm = new ConfirmQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                issued.getQuotation().getAggregateVersion());
        QuotationConfirmation confirmed = commandService.confirmQuotation(owner, confirmKey, confirm);
        FulfillmentItem item = confirmed.getFulfillmentItems().get(0);
        commandService.reportFulfillmentResult(CrmOwner.service(item.getConsumerId()), meta(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 1, "completed", "{}", null));
        commandService.deactivateCustomer(owner, meta(), new DeactivateCustomerCommand(customer.getCustomerId()));
        assertSameResult(issued, commandService.issueQuotation(owner, issueKey, issue));
        assertSameResult(confirmed, commandService.confirmQuotation(owner, confirmKey, confirm));
        assertEquals(FulfillmentState.COMPLETED, items.findById(item.getFulfillmentId()).get().getState());
        assertEquals(3L, auditCount(draft.getQuotation().getQuotationId()));
    }

    @Test
    void draftEditReplayKeepsFirstLineAndTermsAfterAnotherEdit() {
        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放编辑客户"));
        String offeringId = publish("ref-replay-edit", "capability-replay");
        QuotationDraft draft = quote(customer, offeringId);
        CrmCommandMetadata editKey = meta();
        EditQuotationCommand edit = new EditQuotationCommand(draft.getQuotation().getQuotationId(),
                draft.getQuotationVersion().getValidUntil(), Collections.singletonList(new QuotationLineCommand(
                offeringId, new BigDecimal("2"), "SET", new Money(new BigDecimal("10.00"), "CNY"),
                "subject-edited", "scope-edited")));
        QuotationDraft first = commandService.editQuotationDraft(owner, editKey, edit);
        commandService.editQuotationDraft(owner, meta(), new EditQuotationCommand(
                draft.getQuotation().getQuotationId(), draft.getQuotationVersion().getValidUntil(),
                Collections.singletonList(new QuotationLineCommand(offeringId, new BigDecimal("3"), "SET",
                        new Money(new BigDecimal("11.00"), "CNY"), "subject-later", "scope-later"))));
        assertSameResult(first, commandService.editQuotationDraft(owner, editKey, edit));
        assertEquals(3L, auditCount(draft.getQuotation().getQuotationId()));
    }

    @Test
    void governanceReplayKeepsOriginalOwnersContactsAndTransferredQuotations() {
        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放治理客户"));
        quote(customer, publish("ref-replay-governance", "capability-replay"));
        CrmCommandMetadata transferKey = meta();
        TransferCustomerCommand transfer = new TransferCustomerCommand(customer.getCustomerId(),
                CrmOwner.human("first-owner").getOwner());
        CustomerTransferResult first = commandService.transferCustomer(owner, transferKey, transfer);
        commandService.transferCustomer(owner, meta(), new TransferCustomerCommand(customer.getCustomerId(),
                CrmOwner.human("later-owner").getOwner()));
        assertSameResult(first, commandService.transferCustomer(owner, transferKey, transfer));

        Customer source = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放合并源"));
        Customer target = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放合并目标"));
        commandService.createContact(owner, meta(), new CreateContactCommand(source.getCustomerId(), "业务联系人", null));
        CrmCommandMetadata mergeKey = meta();
        MergeCustomerCommand merge = new MergeCustomerCommand(source.getCustomerId(), target.getCustomerId());
        CustomerMergeResult merged = commandService.mergeCustomer(owner, mergeKey, merge);
        commandService.deactivateCustomer(owner, meta(), new DeactivateCustomerCommand(target.getCustomerId()));
        assertSameResult(merged, commandService.mergeCustomer(owner, mergeKey, merge));
        assertEquals(2L, auditCount(source.getCustomerId()));
        assertEquals(3L, auditCount(customer.getCustomerId()));
    }

    @Test
    void quotationTransferDisplayAndVoidRegistrationReplayStayFrozen() {
        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放转移客户"));
        QuotationDraft draft = quote(customer, publish("ref-replay-transfer", "capability-replay"));
        commandService.issueQuotation(owner, meta(), new IssueQuotationCommand(draft.getQuotation().getQuotationId(),
                1, draft.getQuotation().getAggregateVersion()));
        TransferQuotationCommand transfer = new TransferQuotationCommand(draft.getQuotation().getQuotationId(),
                owner.getOwner());
        CrmCommandMetadata transferKey = meta();
        SimpleCrmServerQuotationTransferResult first = commandService.transferQuotationResult(owner, transferKey, transfer);
        assertEquals("issued", first.getDisplayState());
        CrmCommandMetadata withdrawKey = meta();
        WithdrawQuotationCommand withdraw = new WithdrawQuotationCommand(draft.getQuotation().getQuotationId());
        Object withdrawn = commandService.withdrawQuotation(owner, withdrawKey, withdraw);
        assertSameResult(first, commandService.transferQuotationResult(owner, transferKey, transfer));
        assertSameResult(withdrawn, commandService.withdrawQuotation(owner, withdrawKey, withdraw));

        FulfillmentConsumer enabled = new FulfillmentConsumer("consumer-void-replay", "capability-void-replay", 1, true);
        CrmCommandMetadata registerKey = meta();
        commandService.upsertFulfillmentConsumer(owner, registerKey, enabled);
        commandService.upsertFulfillmentConsumer(owner, meta(),
                new FulfillmentConsumer(enabled.getConsumerId(), enabled.getCapability(), 1, false));
        commandService.upsertFulfillmentConsumer(owner, registerKey, enabled);
        assertEquals(false, consumerRepository.listAll().stream()
                .filter(consumer -> consumer.getConsumerId().equals(enabled.getConsumerId())).findFirst().get().isEnabled());
        assertEquals(2L, auditCount(enabled.getConsumerId()));
        assertEquals(4L, auditCount(draft.getQuotation().getQuotationId()));
    }

    /** 真实 MySQL + MVC 响应回归；身份链由独立三权验收覆盖。 */
    @Test
    void createAndRenegotiateReplayExposeCurrentStateWithoutNewFacts() throws Exception {
        SimpleCrmServerOwnerResolverBridge resolver = mock(SimpleCrmServerOwnerResolverBridge.class);
        when(resolver.resolve()).thenReturn(owner);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SimpleCrmServerQuotationController(commandService, queryService, resolver)).build();
        Customer customer = commandService.createCustomer(owner, meta(),
                new CreateCustomerCommand("创建重放客户"));
        String offeringId = publish("ref-replay-create-http", "capability-replay");
        String payload = "{\"customerId\":\"" + customer.getCustomerId()
                + "\",\"validUntil\":\"" + OffsetDateTime.now().plusDays(1)
                + "\",\"lines\":[{\"offeringId\":\"" + offeringId
                + "\",\"quantity\":\"1\",\"unit\":\"SET\","
                + "\"unitPrice\":{\"amount\":\"9.90\",\"currency\":\"CNY\"},"
                + "\"subjectReference\":\"subject-http\",\"fulfillmentScope\":\"scope-http\"}]}";
        String createKey = meta().getIdempotencyKey();
        String sourceId = String.valueOf(SimpleCrmServerJsonCodec.readMap(mvc.perform(
                        post("/api/crm/quotations").header("Idempotency-Key", createKey)
                                .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("draft"))
                .andReturn().getResponse().getContentAsString()).get("quotationId"));
        QuotationIssuance source = commandService.issueQuotation(owner, meta(),
                new IssueQuotationCommand(sourceId, 1, 1));
        assertCreationReplay(mvc, payload, createKey, null, sourceId, "issued",
                source.getQuotation().getAggregateVersion(), null);

        String deriveKey = meta().getIdempotencyKey();
        String derivedId = String.valueOf(SimpleCrmServerJsonCodec.readMap(mvc.perform(
                        post("/api/crm/quotations").param("sourceQuotationId", sourceId)
                                .header("Idempotency-Key", deriveKey))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("draft"))
                .andReturn().getResponse().getContentAsString()).get("quotationId"));
        QuotationIssuance derived = commandService.issueQuotation(owner, meta(),
                new IssueQuotationCommand(derivedId, 1, 1));
        assertCreationReplay(mvc, null, deriveKey, sourceId, derivedId, "issued",
                derived.getQuotation().getAggregateVersion(), null);
        QuotationConfirmation confirmation = commandService.confirmQuotation(owner, meta(),
                new ConfirmQuotationCommand(derivedId, 1, derived.getQuotation().getAggregateVersion()));
        assertCreationReplay(mvc, null, deriveKey, sourceId, derivedId, "confirmed",
                confirmation.getQuotation().getAggregateVersion(), confirmation.getOrder().getOrderId());
        assertCreationReplay(mvc, payload, createKey, null, sourceId, "superseded",
                confirmation.getSupersededQuotation().getAggregateVersion(), null);
        assertEquals(1, confirmation.getFulfillmentItems().size());
    }

    /** 旧创建键只用于查重，不能充当资源转移后的访问凭据。 */
    @Test
    void creationReplayRechecksOwnershipOfCurrentCustomerAndQuotation() {
        CrmCommandMetadata customerKey = meta();
        CreateCustomerCommand customerCommand = new CreateCustomerCommand("归属重放客户");
        Customer moved = commandService.createCustomer(owner, customerKey, customerCommand);
        commandService.transferCustomer(owner, meta(), new TransferCustomerCommand(
                moved.getCustomerId(), CrmOwner.human("replay-other-owner").getOwner()));
        long customerAudit = auditCount(moved.getCustomerId());
        assertEquals(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                assertThrows(CrmException.class, () -> commandService.createCustomer(
                        owner, customerKey, customerCommand)).getErrorCode());
        assertEquals(customerAudit, auditCount(moved.getCustomerId()));

        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("报价归属重放客户"));
        String offeringId = publish("ref-replay-current-owner", "capability-replay");
        CreateQuotationCommand create = new CreateQuotationCommand(customer.getCustomerId(),
                OffsetDateTime.now().plusDays(1).toInstant(), Collections.singletonList(
                new QuotationLineCommand(offeringId, BigDecimal.ONE, "SET", new Money(BigDecimal.TEN, "CNY"),
                        "subject-owner", "scope-owner")));
        CrmCommandMetadata createKey = meta();
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation quote =
                commandService.createQuotationResource(owner, createKey, create);
        commandService.transferQuotation(owner, meta(), new TransferQuotationCommand(quote.getQuotationId(),
                CrmOwner.human("replay-other-owner").getOwner()));
        long quotationAudit = auditCount(quote.getQuotationId());
        assertEquals(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                assertThrows(CrmException.class, () -> commandService.createQuotationResource(
                        owner, createKey, create)).getErrorCode());
        assertEquals(quotationAudit, auditCount(quote.getQuotationId()));

        QuotationDraft source = quote(customer, offeringId);
        commandService.issueQuotation(owner, meta(), new IssueQuotationCommand(
                source.getQuotation().getQuotationId(), 1, source.getQuotation().getAggregateVersion()));
        CrmCommandMetadata deriveKey = meta();
        RenegotiateQuotationCommand derive = new RenegotiateQuotationCommand(source.getQuotation().getQuotationId());
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation derived =
                commandService.renegotiateQuotationResource(owner, deriveKey, derive);
        commandService.transferQuotation(owner, meta(), new TransferQuotationCommand(derived.getQuotationId(),
                CrmOwner.human("replay-other-owner").getOwner()));
        long derivedAudit = auditCount(derived.getQuotationId());
        assertEquals(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                assertThrows(CrmException.class, () -> commandService.renegotiateQuotationResource(
                        owner, deriveKey, derive)).getErrorCode());
        assertEquals(derivedAudit, auditCount(derived.getQuotationId()));
    }

    private void assertCreationReplay(MockMvc mvc, String payload, String key, String sourceId,
                                      String quotationId, String state, long version, String orderId)
            throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long auditBefore = auditCount(quotationId);
        long quotationCount = jdbc.queryForObject("SELECT COUNT(*) FROM crm_quotation", Long.class);
        long orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM crm_order", Long.class);
        long itemCount = jdbc.queryForObject("SELECT COUNT(*) FROM crm_fulfillment_item", Long.class);
        MockHttpServletRequestBuilder request = post("/api/crm/quotations")
                .header("Idempotency-Key", key);
        if (sourceId != null) {
            request.param("sourceQuotationId", sourceId);
        }
        if (payload != null) {
            request.contentType("application/json").content(payload);
        }
        mvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quotationId").value(quotationId))
                .andExpect(jsonPath("$.state").value(state))
                .andExpect(jsonPath("$.aggregateVersion").value(version))
                .andExpect(jsonPath("$.confirmedOrderId").value(orderId));
        assertEquals(auditBefore, auditCount(quotationId));
        assertEquals(quotationCount, jdbc.queryForObject("SELECT COUNT(*) FROM crm_quotation", Long.class).longValue());
        assertEquals(orderCount, jdbc.queryForObject("SELECT COUNT(*) FROM crm_order", Long.class).longValue());
        assertEquals(itemCount, jdbc.queryForObject("SELECT COUNT(*) FROM crm_fulfillment_item", Long.class).longValue());
    }

    private QuotationConfirmation confirmed(String label) {
        Customer customer = commandService.createCustomer(owner, meta(), new CreateCustomerCommand("重放交付客户-" + label));
        QuotationDraft draft = quote(customer, publish("ref-replay-" + label, "capability-replay"));
        QuotationIssuance issuance = commandService.issueQuotation(owner, meta(), new IssueQuotationCommand(
                draft.getQuotation().getQuotationId(), 1, draft.getQuotation().getAggregateVersion()));
        return commandService.confirmQuotation(owner, meta(), new ConfirmQuotationCommand(
                draft.getQuotation().getQuotationId(), 1, issuance.getQuotation().getAggregateVersion()));
    }

    private long auditCount(String resourceId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM crm_audit WHERE target_resource_id = ?", Long.class, resourceId);
    }

    private void assertSameResult(Object first, Object replay) {
        assertEquals(SimpleCrmServerJsonCodec.write(first), SimpleCrmServerJsonCodec.write(replay),
                "旧键重放须完整保留首响应，包含时间、行项、冻结条款和版本");
    }

    private String publish(String reference, String capability) {
        return commandService.publishOffering(owner, meta(), new PublishOfferingCommand(
                commandService.createOffering(owner, meta(), new CreateOfferingCommand(
                        reference, reference, CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                        capability)).getOfferingId())).getOfferingId();
    }

    private QuotationDraft quote(Customer customer, String offeringId) {
        return commandService.createQuotation(owner, meta(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(1).toInstant(),
                        Collections.singletonList(new QuotationLineCommand(offeringId,
                                new BigDecimal("1"), "SET",
                                new Money(new BigDecimal("9.90"), "CNY"),
                                "subject-r", "scope-r"))));
    }

    private CrmCommandMetadata meta() {
        seq.incrementAndGet();
        return new CrmCommandMetadata("replay-" + seq.get(), "rp-" + seq.get());
    }
}
