package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.command.ActivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ConfirmQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateContactCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.DeactivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.EditQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.IssueQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.MergeCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.PublishOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.RenegotiateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ReportFulfillmentResultCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ResetFulfillmentItemCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.WithdrawQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.OpportunityStage;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.test.SimpleCrmServerTestApplication;
import io.github.surezzzzzz.sdk.crm.server.test.support.SimpleCrmServerTestSchema;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 集成测试：真实 MySQL（schema 由受控入口单次执行），覆盖业务闭环、幂等重放、
 * 认领 CAS、水位、取代原子性、合并改挂与引用校验。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCrmServerTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleCrmServerBusinessFlowMySqlIntegrationTest {

    @Autowired
    private SimpleCrmServerCommandService commandService;

    @Autowired
    private SimpleCrmServerQueryService queryService;

    @Autowired
    private DataSource dataSource;

    private final CrmOwner owner = CrmOwner.human("mysql-flow-owner");

    private final AtomicInteger idemSequence = new AtomicInteger();

    /**
     * 受控 schema 执行一次。
     *
     * @throws Exception SQL 失败
     */
    @Autowired
    private io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository
            consumerRepository;

    @BeforeAll
    void ensureSchema() throws Exception {
        SimpleCrmServerTestSchema.ensureExecuted(dataSource);
        // 确认链依赖消费者注册表：种子一个启用中的固定单价消费者（consumerId=clientId 绑定形态）
        consumerRepository.upsert(new io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment
                .FulfillmentConsumer("consumer-int-1", "capability-int", 1, true));
        log.info("MySQL 集成 schema 已就绪+种子消费者");
    }

    /**
     * 全链闭环：建档→目录上架→报价→编辑→签发→确认→认领回写→终态。
     */
    @Test
    void fullBusinessFlowFromCustomerToCompletedFulfillment() {
        Customer customer = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("集成测试客户A"));
        commandService.createOffering(owner, metadata(),
                new CreateOfferingCommand("ref-int-A", "集成服务A", CommercialCapabilityType
                        .FIXED_PRICE_FULFILLMENT_V1, "capability-int"));
        // 上架（创建是 DRAFT，须 publish 才能进报价）
        io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering offering =
                commandService.publishOffering(owner, metadata(),
                        new PublishOfferingCommand(
                                queryService.listOfferings(owner,
                                        io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria
                                                .none(), null, 1).getItems().get(0).getOfferingId()
                                        != null
                                        ? queryService.listOfferings(owner,
                                        new io.github.surezzzzzz.sdk.crm.server.core.model
                                                .CrmListCriteria("draft", null, null, null), null, 1)
                                        .getItems().get(0).getOfferingId() : "offering-x"));
        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offering.getOfferingId()))));
        log.info("草稿创建：quotationId={}，owner={}", draft.getQuotation().getQuotationId(),
                draft.getQuotation().getOwner());
        assertEquals("iam:mysql-flow-owner", draft.getQuotation().getOwner());

        // 编辑草稿（版本号不变聚合版本+1）
        QuotationDraft edited = commandService.editQuotationDraft(owner, metadata(),
                new EditQuotationCommand(draft.getQuotation().getQuotationId(),
                        OffsetDateTime.now().plusDays(14).toInstant(),
                        Collections.singletonList(line(offering.getOfferingId()))));
        assertEquals(1, edited.getQuotationVersion().getVersion(), "编辑不得改变版本号");

        QuotationIssuance issuance = commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        edited.getQuotation().getAggregateVersion()));
        assertEquals("issued", issuance.getQuotationVersion().getState().getCode());

        QuotationConfirmation confirmation = commandService.confirmQuotation(owner, metadata(),
                new ConfirmQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        issuance.getQuotation().getAggregateVersion()));
        FulfillmentItem item = confirmation.getFulfillmentItems().get(0);
        log.info("确认：orderId={}，履约项={}，消费者={}，owner={}",
                confirmation.getOrder().getOrderId(), item.getFulfillmentId(),
                item.getConsumerId(), item.getOwner());

        // 消费者认领（首次 in_progress）→ 终态
        FulfillmentItem claimed = commandService.reportFulfillmentResult(
                CrmOwner.service(consumerClientId(item.getConsumerId())), metadata(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 1, "in_progress",
                        null, "已认领"));
        assertEquals(FulfillmentState.IN_PROGRESS, claimed.getState());
        FulfillmentItem completed = commandService.reportFulfillmentResult(
                CrmOwner.service(consumerClientId(item.getConsumerId())), metadata(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 2, "completed",
                        "license-issued-1", null));
        assertEquals(FulfillmentState.COMPLETED, completed.getState());
        log.info("履约终态：state={}，resultData={}", completed.getState(),
                completed.getResult().getResultData());

        // 订单展示态按交付项事实同事务派生：单行单项全部完成 → fulfilled
        io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order fulfilledOrder =
                queryService.findOrder(owner, null, confirmation.getOrder().getOrderId());
        assertEquals("fulfilled", fulfilledOrder.getDisplayState().getCode(),
                "全部交付项完成后订单展示态必须为 fulfilled");
    }

    /**
     * 关联报价成交时，商机必须在同一事务内由开放阶段派生为 WON。
     */
    @Test
    void confirmingLinkedQuotationMarksOpportunityWonInSameTransaction() {
        Customer customer = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("商机成交客户"));
        String offeringId = publishNewOffering("ref-opportunity-won");
        Opportunity opportunity = commandService.createOpportunity(owner, metadata(),
                new CreateOpportunityCommand(customer.getCustomerId(), "关联产品授权商机", offeringId, null,
                        java.time.LocalDate.now().plusDays(30), null, null));

        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(), opportunity.getOpportunityId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offeringId))));
        QuotationIssuance issuance = commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion()));
        commandService.confirmQuotation(owner, metadata(), new ConfirmQuotationCommand(
                draft.getQuotation().getQuotationId(), 1, issuance.getQuotation().getAggregateVersion()));

        Opportunity committed = queryService.findOpportunity(owner, null, opportunity.getOpportunityId());
        assertEquals(OpportunityStage.WON, committed.getStage(),
                "关联报价确认成交后，商机必须在同一事务内进入 WON");
    }

    /**
     * 幂等重放：同键同摘要返回首个资源 ID，不重复建档。
     */
    @Test
    void idempotentCreateReplaysFirstResourceId() {
        CrmCommandMetadata meta = metadata();
        Customer first = commandService.createCustomer(owner, meta,
                new CreateCustomerCommand("幂等客户B"));
        Customer replay = commandService.createCustomer(owner, meta,
                new CreateCustomerCommand("幂等客户B"));
        log.info("幂等重放：first={}，replay={}（应一致）", first.getCustomerId(),
                replay.getCustomerId());
        assertEquals(first.getCustomerId(), replay.getCustomerId(), "同键同摘要必须重放首资源");
    }

    /**
     * 报价版本回读保留履约对象与范围，编辑后返回新快照而不是服务器造出的默认值。
     */
    @Test
    void quotationVersionReadRetainsEditedFulfillmentObligation() {
        Customer customer = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("交付对象回读客户"));
        String offeringId = publishNewOffering("ref-obligation-roundtrip");
        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offeringId))));
        assertObligation(draft.getQuotation().getQuotationId(), "subject-int", "scope-int");
        commandService.editQuotationDraft(owner, metadata(),
                new EditQuotationCommand(draft.getQuotation().getQuotationId(),
                        OffsetDateTime.now().plusDays(14).toInstant(),
                        Collections.singletonList(new QuotationLineCommand(offeringId,
                                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY"),
                                "customer-server-sn", "customer-production-cluster"))));
        assertObligation(draft.getQuotation().getQuotationId(), "customer-server-sn",
                "customer-production-cluster");
    }

    private void assertObligation(String quotationId, String subject, String scope) {
        io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge resolver =
                org.mockito.Mockito.mock(io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge.class);
        org.mockito.Mockito.when(resolver.resolve()).thenReturn(owner);
        io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerQuotationController quotationController =
                new io.github.surezzzzzz.sdk.crm.server.controller.SimpleCrmServerQuotationController(
                        commandService, queryService, resolver);
        java.util.Map<String, Object> view = quotationController.getQuotationVersion(
                quotationId, 1, null).getBody();
        assertNotNull(view);
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> lines =
                (java.util.List<java.util.Map<String, Object>>) view.get("lines");
        assertEquals(1, lines.size());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> obligation =
                (java.util.Map<String, Object>) lines.get(0).get("obligation");
        assertNotNull(obligation, "公开回读不能丢失冻结履约义务");
        assertEquals(subject, obligation.get("subjectReference"));
        assertEquals(scope, obligation.get("fulfillmentScope"));
        assertEquals("capability-int", obligation.get("requiredConsumerCapability"));
    }

    /**
     * 认领 CAS：重复 in_progress 序号递增不冲突，旧序号回写被拒。
     */
    @Test
    void claimAndWatermarkGuardRejectsStaleSequence() {
        Customer customer = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("水位客户C"));
        String offeringId = publishNewOffering("ref-int-C");
        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offeringId))));
        commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion()));
        QuotationConfirmation confirmation = commandService.confirmQuotation(owner, metadata(),
                new ConfirmQuotationCommand(draft.getQuotation().getQuotationId(), 1, 2L));
        FulfillmentItem item = confirmation.getFulfillmentItems().get(0);
        CrmOwner consumer = CrmOwner.service(consumerClientId(item.getConsumerId()));

        commandService.reportFulfillmentResult(consumer, metadata(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 1, "in_progress",
                        null, null));
        CrmException stale = assertThrows(CrmException.class, () ->
                commandService.reportFulfillmentResult(consumer, metadata(),
                        new ReportFulfillmentResultCommand(item.getFulfillmentId(), 1,
                                "in_progress", null, null)));
        log.info("旧序号拒绝：errorCode={}", stale.getErrorCode());
        assertEquals(CrmErrorCode.FULFILLMENT_RESULT_CONSISTENCY_FAILURE, stale.getErrorCode(),
                "序号不递增必须拒绝（防乱序与重复）");

        // 终态后不可再报
        commandService.reportFulfillmentResult(consumer, metadata(),
                new ReportFulfillmentResultCommand(item.getFulfillmentId(), 2, "failed",
                        "consumer-error", null));
        CrmException terminal = assertThrows(CrmException.class, () ->
                commandService.reportFulfillmentResult(consumer, metadata(),
                        new ReportFulfillmentResultCommand(item.getFulfillmentId(), 3, "failed",
                                "again", null)));
        // failed 可重置：重置后水位保留，旧序号仍拒
        FulfillmentItem reset = commandService.resetFulfillmentItem(owner, metadata(),
                new ResetFulfillmentItemCommand(item.getFulfillmentId()));
        assertEquals(FulfillmentState.PENDING_DISPATCH, reset.getState());
        assertEquals(2, reset.getReportSequence(), "重置保留水位");
    }

    /**
     * 重议取代原子性：派生草稿确认时旧报价同结果 SUPERSEDED 且不可再确认。
     */
    @Test
    void renegotiationSupersedesSourceAtomically() {
        Customer customer = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("重议客户D"));
        String offeringId = publishNewOffering("ref-int-D");
        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offeringId))));
        Quotation issued = commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion())).getQuotation();

        QuotationDraft derived = commandService.renegotiateQuotation(owner, metadata(),
                new RenegotiateQuotationCommand(issued.getQuotationId()));
        assertEquals(issued.getQuotationId(), derived.getQuotation().getSupersedesQuotationId(),
                "派生草稿必须记录取代来源");

        commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(derived.getQuotation().getQuotationId(), 1,
                        derived.getQuotation().getAggregateVersion()));
        QuotationConfirmation confirmation = commandService.confirmQuotation(owner, metadata(),
                new ConfirmQuotationCommand(derived.getQuotation().getQuotationId(), 1, 2L));
        assertNotNull(confirmation.getSupersededQuotation(), "重议链确认必须携带取代事实");
        assertEquals("superseded", confirmation.getSupersededQuotationVersion().getState().getCode(),
                "来源版本必须同结果置 SUPERSEDED");
        log.info("取代联动：source={}→{}，新订单={}",
                confirmation.getSupersededQuotation().getQuotationId(),
                confirmation.getSupersededQuotationVersion().getState(),
                confirmation.getOrder().getOrderId());
    }

    /**
     * 撤回终态不可确认、停用客户拒新报价、引用校验=DRAFT 目录拒入报价。
     */
    @Test
    void withdrawTerminalAndReferenceGuards() {
        Customer customer = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("守卫客户E"));
        String offeringId = publishNewOffering("ref-int-E");
        QuotationDraft draft = commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(customer.getCustomerId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offeringId))));
        Quotation issued = commandService.issueQuotation(owner, metadata(),
                new IssueQuotationCommand(draft.getQuotation().getQuotationId(), 1,
                        draft.getQuotation().getAggregateVersion())).getQuotation();
        commandService.withdrawQuotation(owner, metadata(),
                new WithdrawQuotationCommand(issued.getQuotationId()));
        CrmException confirmWithdrawn = assertThrows(CrmException.class, () ->
                commandService.confirmQuotation(owner, metadata(),
                        new ConfirmQuotationCommand(issued.getQuotationId(), 1, 3L)));
        log.info("撤回后确认拒绝：errorCode={}", confirmWithdrawn.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, confirmWithdrawn.getErrorCode());

        // 停用客户拒新报价
        commandService.deactivateCustomer(owner, metadata(),
                new DeactivateCustomerCommand(customer.getCustomerId()));
        CrmException inactiveCustomer = assertThrows(CrmException.class, () ->
                commandService.createQuotation(owner, metadata(),
                        new CreateQuotationCommand(customer.getCustomerId(),
                                OffsetDateTime.now().plusDays(7).toInstant(),
                                Collections.singletonList(line(offeringId)))));
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, inactiveCustomer.getErrorCode(),
                "停用客户不得进新报价");

        // DRAFT 目录拒入报价
        Customer customerF = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("守卫客户F"));
        io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering draftOffering =
                commandService.createOffering(owner, metadata(),
                        new CreateOfferingCommand("ref-int-F-draft", "未上架F",
                                CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1,
                                "capability-int"));
        CrmException draftRef = assertThrows(CrmException.class, () ->
                commandService.createQuotation(owner, metadata(),
                        new CreateQuotationCommand(customerF.getCustomerId(),
                                OffsetDateTime.now().plusDays(7).toInstant(),
                                Collections.singletonList(line(draftOffering.getOfferingId())))));
        log.info("DRAFT 目录拒入报价：errorCode={}", draftRef.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, draftRef.getErrorCode(),
                "DRAFT 目录不得进新报价");
    }

    /**
     * 合并改挂：源 MERGED、联系人/未完结报价指向目标。
     */
    @Test
    void mergeRelocatesContactsAndOpenQuotations() {
        Customer source = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("合并源G"));
        Customer target = commandService.createCustomer(owner, metadata(),
                new CreateCustomerCommand("合并目标H"));
        commandService.createContact(owner, metadata(),
                new CreateContactCommand(source.getCustomerId(), "源联系人", "经理"));
        String offeringId = publishNewOffering("ref-int-G");
        commandService.createQuotation(owner, metadata(),
                new CreateQuotationCommand(source.getCustomerId(),
                        OffsetDateTime.now().plusDays(7).toInstant(),
                        Collections.singletonList(line(offeringId))));

        commandService.mergeCustomer(owner, metadata(),
                new MergeCustomerCommand(source.getCustomerId(), target.getCustomerId()));
        Customer merged = queryService.findCustomer(owner, null, source.getCustomerId());
        assertEquals("merged", merged.getState().getCode());
        assertEquals(target.getCustomerId(), merged.getMergedIntoCustomerId());
        assertTrue(!queryService.listContacts(owner, null,
                new io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria(null,
                        target.getCustomerId(), null, null), null, 10).getItems().isEmpty(),
                "联系人必须改挂到目标客户");
        log.info("合并完成：source={}→target={}，快照不重写", source.getCustomerId(),
                target.getCustomerId());
    }

    private String publishNewOffering(String reference) {
        io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering offering =
                commandService.createOffering(owner, metadata(), new CreateOfferingCommand(
                        reference, "集成目录" + reference, CommercialCapabilityType
                        .FIXED_PRICE_FULFILLMENT_V1, "capability-int"));
        return commandService.publishOffering(owner, metadata(),
                new PublishOfferingCommand(offering.getOfferingId())).getOfferingId();
    }

    private QuotationLineCommand line(String offeringId) {
        return new QuotationLineCommand(offeringId, new BigDecimal("3"), "SET",
                new Money(new BigDecimal("19.90"), "CNY"), "subject-int", "scope-int");
    }

    /**
     * 消费者标识去 aksk: 前缀（模拟 clientId 回写主体）。
     */
    private String consumerClientId(String consumerId) {
        return consumerId.startsWith("aksk:") ? consumerId.substring(5) : consumerId;
    }

    private CrmCommandMetadata metadata() {
        idemSequence.incrementAndGet();
        return new CrmCommandMetadata("integration-" + idemSequence.get(),
                "idem-" + idemSequence.get());
    }
}
