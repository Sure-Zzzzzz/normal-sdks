package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.core.command.ActivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ConfirmQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateContactCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOpportunityCommand;
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
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransitionOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.WithdrawQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityRegistry;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentResult;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.OpportunityStage;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraftService;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationLifecycleService;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationWithdrawal;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerMergeResult;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerTransferResult;
import io.github.surezzzzzz.sdk.crm.server.core.port.audit.CrmAuditPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmCreateIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmReplayableIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmClock;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcContactRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcCustomerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentConsumerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOfferingRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOpportunityRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOrderRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcQuotationRepository;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerQuotationTransferResult;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerJsonCodec;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerTimeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Instant;

/**
 * CRM 命令编排服务：短事务内完成幂等判定、领域服务、条件写入与业务审计。
 *
 * <p>行级数据范围（owner 等值/IN）由查询仓储按 DATA 计划并入 SQL；写路径上的资源加载
 * 经点查后由领域方法校验状态与归属一致性。失败整体回滚不留部分事实。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleCrmServerCommandService {

    private final SimpleCrmServerJdbcCustomerRepository customerRepository;
    private final SimpleCrmServerJdbcContactRepository contactRepository;
    private final SimpleCrmServerJdbcOfferingRepository offeringRepository;
    private final SimpleCrmServerJdbcQuotationRepository quotationRepository;
    private final SimpleCrmServerJdbcOpportunityRepository opportunityRepository;
    private final SimpleCrmServerJdbcOrderRepository orderRepository;
    private final SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository;
    private final SimpleCrmServerJdbcFulfillmentConsumerRepository consumerRepository;
    private final CrmCreateIdempotencyPort createIdempotency;
    private final CrmReplayableIdempotencyPort replayableIdempotency;
    private final CrmAuditPort auditPort;
    private final CrmClock clock;
    private final CrmIdGenerator idGenerator;
    private final QuotationDraftService draftService;
    private final QuotationLifecycleService lifecycleService;

    /**
     * 创建命令编排服务（装配由 AutoConfiguration 提供）。
     *
     * @param customerRepository 客户仓储
     * @param contactRepository 联系人仓储
     * @param offeringRepository 目录仓储
     * @param quotationRepository 报价仓储
     * @param orderRepository 订单仓储
     * @param fulfillmentItemRepository 履约项仓储
     * @param consumerRepository 消费者注册表仓储
     * @param createIdempotency 创建幂等 Port
     * @param replayableIdempotency 已知目标幂等 Port
     * @param auditPort 审计 Port
     * @param clock 时钟
     * @param idGenerator ID 生成器
     */
    public SimpleCrmServerCommandService(SimpleCrmServerJdbcCustomerRepository customerRepository,
                             SimpleCrmServerJdbcContactRepository contactRepository,
                             SimpleCrmServerJdbcOfferingRepository offeringRepository,
                             SimpleCrmServerJdbcQuotationRepository quotationRepository,
                             SimpleCrmServerJdbcOpportunityRepository opportunityRepository,
                             SimpleCrmServerJdbcOrderRepository orderRepository,
                             SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository,
                             SimpleCrmServerJdbcFulfillmentConsumerRepository consumerRepository,
                             CrmCreateIdempotencyPort createIdempotency,
                             CrmReplayableIdempotencyPort replayableIdempotency,
                             CrmAuditPort auditPort, CrmClock clock, CrmIdGenerator idGenerator) {
        this.customerRepository = customerRepository;
        this.contactRepository = contactRepository;
        this.offeringRepository = offeringRepository;
        this.quotationRepository = quotationRepository;
        this.opportunityRepository = opportunityRepository;
        this.orderRepository = orderRepository;
        this.fulfillmentItemRepository = fulfillmentItemRepository;
        this.consumerRepository = consumerRepository;
        this.createIdempotency = createIdempotency;
        this.replayableIdempotency = replayableIdempotency;
        this.auditPort = auditPort;
        this.clock = () -> SimpleCrmServerTimeHelper.truncateToMillis(clock.now());
        this.idGenerator = idGenerator;
        this.draftService = new QuotationDraftService(new CommercialCapabilityRegistry(
                Collections.singletonList(
                        new io.github.surezzzzzz.sdk.crm.server.core.domain.commercial
                                .FixedPriceFulfillmentCapability())));
        this.lifecycleService = new QuotationLifecycleService();
    }

    /** 创建销售商机。 */
    @Transactional
    public Opportunity createOpportunity(CrmOwner owner, CrmCommandMetadata metadata,
                                         CreateOpportunityCommand command) {
        Customer customer = customerRepository.findById(command.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "customer"));
        requireOwnership(owner, customer.getOwner(), customer.getCustomerId(), "customer");
        if (command.getContactId() != null) {
            Contact contact = contactRepository.findById(command.getContactId())
                    .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "contact"));
            if (!command.getCustomerId().equals(contact.getCustomerId())) {
                throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "contact");
            }
        }
        if (command.getPrimaryOfferingId() != null) {
            Offering offering = offeringRepository.findById(command.getPrimaryOfferingId())
                    .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "offering"));
        }
        String id = createIdempotency.execute(owner, metadata, CrmCommandType.CREATE_OPPORTUNITY,
                CrmResourceType.OPPORTUNITY, digest(metadata, command), () -> {
                    Customer currentCustomer = customerRepository.findById(command.getCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "customer"));
                    if (currentCustomer.getState()
                            != io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.ACTIVE) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
                    }
                    if (command.getPrimaryOfferingId() != null) {
                        Offering currentOffering = offeringRepository.findById(command.getPrimaryOfferingId())
                                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                        "offering"));
                        if (!currentOffering.isPublished()) {
                            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                                    "offering is not published");
                        }
                    }
                    Instant now = clock.now();
                    Opportunity value = new Opportunity(idGenerator.nextId(
                            io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType.OPPORTUNITY),
                            command.getCustomerId(), owner.getOwner(), command.getTitle(), command.getPrimaryOfferingId(),
                            command.getContactId(), command.getExpectedCloseDate(), command.getEstimatedAmount(),
                            command.getCurrency(), OpportunityStage.NEW, null, 1L, now, now);
                    Opportunity inserted = opportunityRepository.insert(value);
                    auditPort.record(owner, CrmAction.OPPORTUNITY_CREATE, CrmResourceType.OPPORTUNITY,
                            inserted.getOpportunityId(), inserted.getAggregateVersion(), metadata.getCorrelationId(), null, now);
                    return inserted.getOpportunityId();
                });
        return opportunityRepository.findById(id)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity"));
    }

    /** 推进商机阶段；won 只由报价成交派生。 */
    @Transactional
    public Opportunity transitionOpportunity(CrmOwner owner, CrmCommandMetadata metadata,
                                              TransitionOpportunityCommand command) {
        Opportunity current = loadOpportunity(command.getOpportunityId());
        requireOwnership(owner, current.getOwner(), current.getOpportunityId(), "opportunity");
        if (command.getStage() == OpportunityStage.WON) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "won is derived from quotation confirmation");
        }
        return replayableIdempotency.execute(owner, metadata, CrmCommandType.TRANSITION_OPPORTUNITY,
                CrmResourceType.OPPORTUNITY, current.getOpportunityId(), digest(metadata, command), () -> {
                    Opportunity next = current.progressedTo(command.getStage(), command.getLostReason(), clock.now());
                    opportunityRepository.update(next, current.getAggregateVersion());
                    auditPort.record(owner, CrmAction.OPPORTUNITY_STAGE, CrmResourceType.OPPORTUNITY,
                            next.getOpportunityId(), next.getAggregateVersion(), metadata.getCorrelationId(), null, clock.now());
                    return next;
                }, () -> opportunityRepository.findById(current.getOpportunityId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity")));
    }

    /** 转移商机归属。 */
    @Transactional
    public Opportunity transferOpportunity(CrmOwner owner, CrmCommandMetadata metadata,
                                            TransferOpportunityCommand command) {
        Opportunity current = loadOpportunity(command.getOpportunityId());
        return replayableIdempotency.execute(owner, metadata, CrmCommandType.TRANSFER_OPPORTUNITY,
                CrmResourceType.OPPORTUNITY, current.getOpportunityId(), digest(metadata, command), () -> {
                    Opportunity next = current.transferredTo(command.getNewOwner(), clock.now());
                    Customer customer = customerRepository.findById(current.getCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "customer"));
                    if (!customer.getOwner().equals(command.getNewOwner())) {
                        customerRepository.update(customer.transferredTo(command.getNewOwner(), clock.now()),
                                customer.getAggregateVersion());
                    }
                    for (Quotation quotation : quotationRepository.findOpenByCustomer(
                            current.getCustomerId())) {
                        if (!quotation.getOwner().equals(command.getNewOwner())) {
                            quotationRepository.updateOwner(quotation, command.getNewOwner());
                        }
                    }
                    for (Opportunity opportunity : opportunityRepository.findOpenByCustomer(
                            current.getCustomerId())) {
                        if (opportunity.getOpportunityId().equals(current.getOpportunityId())) {
                            opportunityRepository.update(next, current.getAggregateVersion());
                        } else if (!opportunity.getOwner().equals(command.getNewOwner())) {
                            opportunityRepository.update(opportunity.transferredTo(command.getNewOwner(), clock.now()),
                                    opportunity.getAggregateVersion());
                        }
                    }
                    auditPort.record(owner, CrmAction.OPPORTUNITY_TRANSFER, CrmResourceType.OPPORTUNITY,
                            next.getOpportunityId(), next.getAggregateVersion(), metadata.getCorrelationId(), null, clock.now());
                    return next;
                }, () -> opportunityRepository.findById(current.getOpportunityId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity")));
    }

    /** 成交事务内推进关联商机到 won。 */
    private void markOpportunityWon(CrmOwner owner, String opportunityId, String correlationId) {
        if (opportunityId == null) {
            return;
        }
        Opportunity current = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity"));
        requireOwnership(owner, current.getOwner(), current.getOpportunityId(), "opportunity");
        if (current.getStage() == OpportunityStage.WON) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                    "opportunity already won by another quotation");
        }
        Opportunity won = current.won(clock.now());
        opportunityRepository.update(won, current.getAggregateVersion());
        auditPort.record(owner, CrmAction.OPPORTUNITY_STAGE, CrmResourceType.OPPORTUNITY,
                won.getOpportunityId(), won.getAggregateVersion(), correlationId, null, clock.now());
    }

    /**
     * 创建客户（owner=创建者）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 客户事实
     */
    @Transactional
    public Customer createCustomer(CrmOwner owner, CrmCommandMetadata metadata,
                                   CreateCustomerCommand command) {
        String customerId = createIdempotency.execute(owner, metadata, CrmCommandType.CREATE_CUSTOMER,
                CrmResourceType.CUSTOMER, digest(metadata, command),
                () -> {
                    Customer created = new Customer(idGenerator.nextId(
                            io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType.CUSTOMER),
                            command.getDisplayName(),
                            io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.ACTIVE,
                            owner.getOwner(), null, 1L, clock.now(), clock.now());
                    Customer inserted = customerRepository.insert(created);
                    // 审计在首执行闭包内：重放不重复写审计/发事件
                    auditPort.record(owner, CrmAction.CUSTOMER_CREATE, CrmResourceType.CUSTOMER,
                            inserted.getCustomerId(), inserted.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return inserted.getCustomerId();
                });
        Customer current = customerRepository.findById(customerId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        // 创建重放回读当前事实，资源转移后的旧键不能绕过当前归属校验。
        requireOwnership(owner, current.getOwner(), current.getCustomerId(), "customer");
        return current;
    }

    /**
     * 创建联系人（校验客户存在且未停用，归属一致性由 DATA 层保证加载范围）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 联系人事实
     */
    @Transactional
    public Contact createContact(CrmOwner owner, CrmCommandMetadata metadata,
                                 CreateContactCommand command) {
        Customer customer = customerRepository.findById(command.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        requireOwnership(owner, customer.getOwner(), customer.getCustomerId(), "customer");
        String contactId = createIdempotency.execute(owner, metadata, CrmCommandType.CREATE_CONTACT,
                CrmResourceType.CONTACT, digest(metadata, command), () -> {
                    Customer currentCustomer = customerRepository.findById(command.getCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "customer"));
                    if (currentCustomer.getState()
                            != io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.ACTIVE) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
                    }
                    Contact created = new Contact(idGenerator.nextId(
                            io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType.CONTACT),
                            command.getCustomerId(), command.getDisplayName(), command.getTitle(),
                            io.github.surezzzzzz.sdk.crm.server.core.domain.contact.ContactState.ACTIVE,
                            1L, clock.now(), clock.now());
                    Contact inserted = contactRepository.insert(created);
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.CONTACT_CREATE, CrmResourceType.CONTACT,
                            inserted.getContactId(), inserted.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return inserted.getContactId();
                });
        return contactRepository.findById(contactId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "contact"));
    }

    /**
     * 创建目录项（DRAFT 录入态，须另行上架才可进新报价）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 目录事实
     */
    @Transactional
    public Offering createOffering(CrmOwner owner, CrmCommandMetadata metadata,
                                   CreateOfferingCommand command) {
        String offeringId = createIdempotency.execute(owner, metadata, CrmCommandType.CREATE_OFFERING,
                CrmResourceType.OFFERING, digest(metadata, command), () -> {
                    Offering created = new Offering(idGenerator.nextId(
                            io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType.OFFERING),
                            command.getOfferingReference(), command.getDisplayName(),
                            io.github.surezzzzzz.sdk.crm.server.core.domain.offering.OfferingState.DRAFT,
                            command.getCapabilityType(), command.getRequiredConsumerCapability(),
                            owner.getOwner(), 1L, clock.now(), clock.now());
                    Offering inserted = offeringRepository.insert(created);
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.OFFERING_CREATE, CrmResourceType.OFFERING,
                            inserted.getOfferingId(), inserted.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return inserted.getOfferingId();
                });
        return offeringRepository.findById(offeringId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "offering"));
    }

    /**
     * 创建报价草稿。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 草稿事实对
     */
    @Transactional
    public QuotationDraft createQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                          CreateQuotationCommand command) {
        Quotation quotation = createQuotationResource(owner, metadata, command);
        QuotationVersion version = quotationRepository.findVersion(quotation.getQuotationId(),
                quotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        return new QuotationDraft(quotation, version);
    }

    /**
     * 创建命令的 HTTP 读模型：幂等重放只复用资源 ID，并按当前归属回读报价事实。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 创建命令
     * @return 当前报价事实
     */
    @Transactional
    public Quotation createQuotationResource(CrmOwner owner, CrmCommandMetadata metadata,
                                              CreateQuotationCommand command) {
        CreateQuotationCommand persistedCommand = new CreateQuotationCommand(command.getCustomerId(),
                command.getOpportunityId(), SimpleCrmServerTimeHelper.truncateToMillis(command.getValidUntil()), command.getLines());
        String quotationId = createIdempotency.execute(owner, metadata,
                CrmCommandType.CREATE_QUOTATION, CrmResourceType.QUOTATION, digest(metadata, command),
                () -> {
                    Customer currentCustomer = customerRepository.findById(command.getCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "customer"));
                    requireOwnership(owner, currentCustomer.getOwner(), currentCustomer.getCustomerId(), "customer");
                    List<Offering> currentOfferings = loadUniqueOfferings(command.getLines());
                    if (command.getOpportunityId() != null) {
                        Opportunity currentOpportunity = opportunityRepository.findById(command.getOpportunityId())
                                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                        "opportunity"));
                        requireOwnership(owner, currentOpportunity.getOwner(), currentOpportunity.getOpportunityId(),
                                "opportunity");
                        if (!command.getCustomerId().equals(currentOpportunity.getCustomerId())) {
                            throw new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity");
                        }
                        if (currentOpportunity.getStage() == OpportunityStage.WON
                                || currentOpportunity.getStage() == OpportunityStage.LOST) {
                            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                                    "terminal opportunity cannot quote");
                        }
                    }
                    QuotationDraft draft = draftService.create(owner, currentCustomer, persistedCommand,
                            currentOfferings,
                            clock.now(), idGenerator);
                    quotationRepository.insert(draft.getQuotation());
                    quotationRepository.insertVersion(draft.getQuotationVersion());
                    auditPort.record(owner, CrmAction.QUOTATION_CREATE, CrmResourceType.QUOTATION,
                            draft.getQuotation().getQuotationId(), draft.getQuotation().getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return draft.getQuotation().getQuotationId();
                });
        Quotation current = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotation"));
        requireOwnership(owner, current.getOwner(), current.getQuotationId(), "quotation");
        return current;
    }

    /**
     * 编辑报价草稿（整单替换，版本号不变聚合版本递增）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 编辑后草稿
     */
    @Transactional
    public QuotationDraft editQuotationDraft(CrmOwner owner, CrmCommandMetadata metadata,
                                             EditQuotationCommand command) {
        Quotation quotation = loadQuotation(command.getQuotationId());
        requireOwnership(owner, quotation.getOwner(), quotation.getQuotationId(), "quotation");
        QuotationVersion draft = quotationRepository.findVersion(quotation.getQuotationId(),
                quotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        Customer customer = customerRepository.findById(quotation.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        Opportunity quotationOpportunity = null;
        if (quotation.getOpportunityId() != null) {
            quotationOpportunity = opportunityRepository.findById(quotation.getOpportunityId())
                    .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                            "opportunity"));
            requireOwnership(owner, quotationOpportunity.getOwner(), quotationOpportunity.getOpportunityId(),
                    "opportunity");
        }
        final Opportunity linkedOpportunity = quotationOpportunity;
        List<Offering> offerings = loadUniqueOfferings(command.getLines());
        EditQuotationCommand persistedCommand = new EditQuotationCommand(command.getQuotationId(),
                SimpleCrmServerTimeHelper.truncateToMillis(command.getValidUntil()), command.getLines());
        QuotationDraft edited = replayableIdempotency.execute(owner, metadata,
                CrmCommandType.EDIT_QUOTATION, CrmResourceType.QUOTATION,
                quotation.getQuotationId(), digest(metadata, command), () -> {
                    if (linkedOpportunity != null
                            && (linkedOpportunity.getStage() == OpportunityStage.WON
                            || linkedOpportunity.getStage() == OpportunityStage.LOST)) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                                "terminal opportunity cannot edit quotation");
                    }
                    QuotationDraft draftEdited = draftService.edit(quotation, draft, persistedCommand,
                            customer, offerings, clock.now(), idGenerator);
                    quotationRepository.update(draftEdited.getQuotation(),
                            quotation.getAggregateVersion());
                    // 编辑必须整版替换版本行（有效期/金额/行明细在版本上，只更新聚合不更新版本=编辑丢失）
                    quotationRepository.replaceVersion(draftEdited.getQuotationVersion());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.QUOTATION_EDIT, CrmResourceType.QUOTATION,
                            quotation.getQuotationId(),
                            draftEdited.getQuotation().getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return draftEdited;
                }, () -> {
                    // 重放：按已提交的草稿编辑事实重建（编辑不改版本号，直接读当前版本）
                    Quotation committed = loadQuotation(quotation.getQuotationId());
                    QuotationVersion committedVersion = quotationRepository.findVersion(
                            quotation.getQuotationId(), committed.getCurrentVersion())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "quotationVersion"));
                    return new QuotationDraft(committed, committedVersion);
                });
        return edited;
    }

    /**
     * 签发报价（版本冻结，可确认）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 签发事实对
     */
    @Transactional
    public QuotationIssuance issueQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                            IssueQuotationCommand command) {
        Quotation quotation = loadQuotation(command.getQuotationId());
        requireOwnership(owner, quotation.getOwner(), quotation.getQuotationId(), "quotation");
        Customer issueCustomer = customerRepository.findById(quotation.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        QuotationVersion version = quotationRepository.findVersion(quotation.getQuotationId(),
                quotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        return replayableIdempotency.execute(owner, metadata, CrmCommandType.ISSUE_QUOTATION,
                CrmResourceType.QUOTATION, quotation.getQuotationId(), digest(metadata, command), () -> {
                    if (issueCustomer.getState() != io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.ACTIVE) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
                    }
                    // 版本校验在首执行闭包内：重放路径不经此（重放时聚合版本已前进属正常）
                    requireExpectedAggregateVersion(command.getExpectedAggregateVersion(), quotation);
                    QuotationIssuance issuance = lifecycleService.issue(quotation, version,
                            clock.now(), owner.getOwner());
                    quotationRepository.update(issuance.getQuotation(),
                            quotation.getAggregateVersion());
                    quotationRepository.transitionVersionState(issuance.getQuotationVersion(),
                            io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState.DRAFT);
                    auditPort.record(owner, CrmAction.QUOTATION_ISSUE, CrmResourceType.QUOTATION,
                            quotation.getQuotationId(), issuance.getQuotation().getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return issuance;
                }, () -> {
                    // 重放：按已提交的 ISSUED 版本事实重建签发声明（不重复迁移、不重复审计）
                    io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion
                            issuedVersion = quotationRepository.findVersion(
                            quotation.getQuotationId(), quotation.getCurrentVersion())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "quotationVersion"));
                    return new io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance(
                            quotation, issuedVersion);
                });
    }

    /**
     * 确认报价（生成订单+履约项；重议链同事务取代旧报价）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 确认事实集合
     */
    @Transactional
    public QuotationConfirmation confirmQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                                  ConfirmQuotationCommand command) {
        Quotation quotation = loadQuotation(command.getQuotationId());
        requireOwnership(owner, quotation.getOwner(), quotation.getQuotationId(), "quotation");
        // 确认前复检客户未停用（停用客户不应产生新成交）
        Customer confirmCustomer = customerRepository.findById(quotation.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        QuotationVersion version = quotationRepository.findVersion(quotation.getQuotationId(),
                quotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        Quotation supersedeTarget = null;
        QuotationVersion supersedeTargetVersion = null;
        if (quotation.getSupersedesQuotationId() != null) {
            supersedeTarget = loadQuotation(quotation.getSupersedesQuotationId());
            requireOwnership(owner, supersedeTarget.getOwner(), supersedeTarget.getQuotationId(),
                    "quotation");
            supersedeTargetVersion = quotationRepository.findVersion(supersedeTarget.getQuotationId(),
                    supersedeTarget.getCurrentVersion())
                    .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                            "quotationVersion"));
        }
        Quotation q = quotation;
        QuotationVersion v = version;
        Quotation st = supersedeTarget;
        QuotationVersion stv = supersedeTargetVersion;
        return replayableIdempotency.execute(owner, metadata, CrmCommandType.CONFIRM_QUOTATION,
                CrmResourceType.QUOTATION, quotation.getQuotationId(), digest(metadata, command), () -> {
                    if (confirmCustomer.getState() != io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.ACTIVE) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
                    }
                    // 版本校验在首执行闭包内：重放路径不经此（重放时聚合版本已前进属正常）
                    requireExpectedAggregateVersion(command.getExpectedAggregateVersion(), q);
                    QuotationConfirmation confirmation = lifecycleService.confirm(q, v, clock.now(),
                            owner.getOwner(), idGenerator,
                            new SimpleCrmServerConsumerSelectorAdapter(consumerRepository), st, stv);
                    quotationRepository.update(confirmation.getQuotation(), q.getAggregateVersion());
                    markOpportunityWon(owner, confirmation.getQuotation().getOpportunityId(), metadata.getCorrelationId());
                    // 版本行状态必须同事务落 CONFIRMED（否则读回装配永远停在 issued）
                    quotationRepository.transitionVersionState(confirmation.getQuotationVersion(),
                            io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState.ISSUED);
                    orderRepository.insert(confirmation.getOrder());
                    for (FulfillmentItem item : confirmation.getFulfillmentItems()) {
                        fulfillmentItemRepository.insert(item);
                    }
                    if (confirmation.getSupersededQuotation() != null) {
                        quotationRepository.update(confirmation.getSupersededQuotation(),
                                st.getAggregateVersion());
                        // 被取代版本行同步落 SUPERSEDED
                        quotationRepository.transitionVersionState(
                                confirmation.getSupersededQuotationVersion(),
                                io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState.ISSUED);
                    }
                    log.info("报价确认完成：quotationId={}，orderId={}，履约项数={}，取代来源={}",
                            q.getQuotationId(), confirmation.getOrder().getOrderId(),
                            confirmation.getFulfillmentItems().size(),
                            confirmation.getSupersededQuotation() == null ? "无"
                                    : confirmation.getSupersededQuotation().getQuotationId());
                    auditPort.record(owner, CrmAction.QUOTATION_CONFIRM, CrmResourceType.QUOTATION,
                            q.getQuotationId(), confirmation.getQuotation().getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return confirmation;
                }, () -> {
                    // 重放：按已提交的 CONFIRMED 事实重建确认集合（订单+履约+版本+取代），不重复迁移不重复审计
                    io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation
                            committedQuotation = loadQuotation(q.getQuotationId());
                    io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion
                            committedVersion = quotationRepository.findVersion(
                            q.getQuotationId(), committedQuotation.getCurrentVersion())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "quotationVersion"));
                    io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order committedOrder =
                            orderRepository.findByQuotationVersion(q.getQuotationId(),
                                    committedVersion.getVersion())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "order"));
                    io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation
                            rebuilt = new io.github.surezzzzzz.sdk.crm.server.core.domain.quotation
                            .QuotationConfirmation(committedQuotation, committedVersion,
                            committedOrder, fulfillmentItemRepository.findByOrderId(
                            committedOrder.getOrderId()));
                    return rebuilt;
                });
    }

    /**
     * 撤回已签发报价。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 撤回事实对
     */
    @Transactional
    public QuotationWithdrawal withdrawQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                                 WithdrawQuotationCommand command) {
        Quotation quotation = loadQuotation(command.getQuotationId());
        requireOwnership(owner, quotation.getOwner(), quotation.getQuotationId(), "quotation");
        QuotationVersion version = quotationRepository.findVersion(quotation.getQuotationId(),
                quotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.WITHDRAW_QUOTATION, CrmResourceType.QUOTATION,
                quotation.getQuotationId(), digest(metadata, command), () -> {
                    QuotationWithdrawal withdrawal = lifecycleService.withdraw(quotation, version,
                            clock.now(), owner.getOwner());
                    quotationRepository.update(withdrawal.getQuotation(),
                            quotation.getAggregateVersion());
                    // 撤回版本行必须同事务落 WITHDRAWN 终态（否则版本读回永远停在 ISSUED）
                    quotationRepository.transitionVersionState(withdrawal.getQuotationVersion(),
                            io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationState.ISSUED);
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.QUOTATION_WITHDRAW, CrmResourceType.QUOTATION,
                            quotation.getQuotationId(),
                            withdrawal.getQuotation().getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return withdrawal;
                }, () -> {
                    // 重放：按已提交的 WITHDRAWN 事实重建
                    Quotation committed = loadQuotation(quotation.getQuotationId());
                    QuotationVersion committedVersion = quotationRepository.findVersion(
                            quotation.getQuotationId(), committed.getCurrentVersion())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "quotationVersion"));
                    return new QuotationWithdrawal(committed, committedVersion);
                });
    }

    /**
     * 重议派生新草稿（快照复制+取代链）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 新草稿
     */
    @Transactional
    public QuotationDraft renegotiateQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                               RenegotiateQuotationCommand command) {
        Quotation derivedQuotation = renegotiateQuotationResource(owner, metadata, command);
        QuotationVersion derivedVersion = quotationRepository.findVersion(derivedQuotation.getQuotationId(),
                derivedQuotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        return new QuotationDraft(derivedQuotation, derivedVersion);
    }

    /**
     * 重议创建的 HTTP 读模型：幂等重放只复用派生报价 ID，并按当前归属回读事实。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 重议命令
     * @return 当前派生报价事实
     */
    @Transactional
    public Quotation renegotiateQuotationResource(CrmOwner owner, CrmCommandMetadata metadata,
                                                   RenegotiateQuotationCommand command) {
        String derivedId = createIdempotency.execute(owner, metadata,
                CrmCommandType.RENEGOTIATE_QUOTATION, CrmResourceType.QUOTATION,
                digest(metadata, command), () -> {
                    Quotation source = loadQuotation(command.getSourceQuotationId());
                    requireOwnership(owner, source.getOwner(), source.getQuotationId(), "quotation");
                    QuotationVersion sourceVersion = quotationRepository.findVersion(
                            source.getQuotationId(), source.getCurrentVersion())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "quotationVersion"));
                    Customer currentSourceCustomer = customerRepository.findById(source.getCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "customer"));
                    Opportunity currentSourceOpportunity = null;
                    if (source.getOpportunityId() != null) {
                        currentSourceOpportunity = opportunityRepository.findById(
                                source.getOpportunityId()).orElseThrow(() -> new CrmException(
                                CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity"));
                        requireOwnership(owner, currentSourceOpportunity.getOwner(),
                                currentSourceOpportunity.getOpportunityId(), "opportunity");
                    }
                    if (currentSourceCustomer.getState()
                            != io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.ACTIVE) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "customer is not active");
                    }
                    if (currentSourceOpportunity != null) {
                        if (currentSourceOpportunity.getStage() == OpportunityStage.WON
                                || currentSourceOpportunity.getStage() == OpportunityStage.LOST) {
                            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                                    "terminal opportunity cannot renegotiate quotation");
                        }
                    }
                    QuotationDraft derived = lifecycleService.renegotiate(source, sourceVersion,
                            clock.now(), idGenerator);
                    quotationRepository.insert(derived.getQuotation());
                    quotationRepository.insertVersion(derived.getQuotationVersion());
                    auditPort.record(owner, CrmAction.QUOTATION_RENEGOTIATE, CrmResourceType.QUOTATION,
                            derived.getQuotation().getQuotationId(), derived.getQuotation().getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return derived.getQuotation().getQuotationId();
                });
        Quotation current = quotationRepository.findById(derivedId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotation"));
        requireOwnership(owner, current.getOwner(), current.getQuotationId(), "quotation");
        return current;
    }

    /**
     * 停用客户。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 停用后客户
     */
    @Transactional
    public Customer deactivateCustomer(CrmOwner owner, CrmCommandMetadata metadata,
                                       DeactivateCustomerCommand command) {
        Customer customer = customerRepository.findById(command.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        requireOwnership(owner, customer.getOwner(), customer.getCustomerId(), "customer");
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.DEACTIVATE_CUSTOMER, CrmResourceType.CUSTOMER,
                customer.getCustomerId(), digest(metadata, command), () -> {
                    Customer deactivated = customer.deactivated(clock.now());
                    customerRepository.update(deactivated, customer.getAggregateVersion());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.CUSTOMER_DEACTIVATE, CrmResourceType.CUSTOMER,
                            customer.getCustomerId(), deactivated.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return deactivated;
                }, () -> customerRepository.findById(customer.getCustomerId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                "customer")));
    }

    /**
     * 复启客户。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 复启后客户
     */
    @Transactional
    public Customer activateCustomer(CrmOwner owner, CrmCommandMetadata metadata,
                                     ActivateCustomerCommand command) {
        Customer customer = customerRepository.findById(command.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        requireOwnership(owner, customer.getOwner(), customer.getCustomerId(), "customer");
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.ACTIVATE_CUSTOMER, CrmResourceType.CUSTOMER,
                customer.getCustomerId(), digest(metadata, command), () -> {
                    Customer activated = customer.activated(clock.now());
                    customerRepository.update(activated, customer.getAggregateVersion());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.CUSTOMER_ACTIVATE, CrmResourceType.CUSTOMER,
                            customer.getCustomerId(), activated.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return activated;
                }, () -> customerRepository.findById(customer.getCustomerId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                "customer")));
    }

    /**
     * 上架目录。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 上架后目录
     */
    @Transactional
    public Offering publishOffering(CrmOwner owner, CrmCommandMetadata metadata,
                                    PublishOfferingCommand command) {
        Offering offering = offeringRepository.findById(command.getOfferingId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "offering"));
        requireOwnership(owner, offering.getOwner(), offering.getOfferingId(), "offering");
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.PUBLISH_OFFERING, CrmResourceType.OFFERING,
                offering.getOfferingId(), digest(metadata, command), () -> {
                    Offering published = offering.published(clock.now());
                    offeringRepository.update(published, offering.getAggregateVersion());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.OFFERING_PUBLISH, CrmResourceType.OFFERING,
                            offering.getOfferingId(), published.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return published;
                }, () -> offeringRepository.findById(offering.getOfferingId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                "offering")));
    }

    /**
     * 下架目录。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 下架后目录
     */
    @Transactional
    public Offering disableOffering(CrmOwner owner, CrmCommandMetadata metadata,
                                    DisableOfferingCommand command) {
        Offering offering = offeringRepository.findById(command.getOfferingId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "offering"));
        requireOwnership(owner, offering.getOwner(), offering.getOfferingId(), "offering");
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.DISABLE_OFFERING, CrmResourceType.OFFERING,
                offering.getOfferingId(), digest(metadata, command), () -> {
                    Offering disabled = offering.disabled(clock.now());
                    offeringRepository.update(disabled, offering.getAggregateVersion());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.OFFERING_DISABLE, CrmResourceType.OFFERING,
                            offering.getOfferingId(), disabled.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return disabled;
                }, () -> offeringRepository.findById(offering.getOfferingId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                "offering")));
    }

    /**
     * 合并客户：源置 MERGED、联系人/未完结报价/订单同事务改挂，快照不重写。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 合并事实集合
     */
    @Transactional
    public CustomerMergeResult mergeCustomer(CrmOwner owner, CrmCommandMetadata metadata,
                                             MergeCustomerCommand command) {
        // 治理动作（设计 §7.5）：跨 owner 是该动作的能力而非越权——准入由专属 API 码
        //（crm.customer.merge，仅 supervisor/admin 角色授予）在控制器层强制；写路径不做 owner 等值拦截。
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.MERGE_CUSTOMER, CrmResourceType.CUSTOMER,
                command.getSourceCustomerId(), digest(metadata, command), () -> {
                    Customer currentSource = customerRepository.findById(command.getSourceCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "customer"));
                    Customer currentTarget = customerRepository.findById(command.getTargetCustomerId())
                            .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                    "customer"));
                    if (currentTarget.getState()
                            == io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState.MERGED) {
                        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                                "merged customer cannot be a merge target");
                    }
                    Customer mergedSource = currentSource.mergedInto(currentTarget.getCustomerId(), clock.now());
                    customerRepository.update(mergedSource, currentSource.getAggregateVersion());
                    contactRepository.relocateCustomer(currentSource.getCustomerId(),
                            currentTarget.getCustomerId());
                    int movedQuotations = quotationRepository.relocateCustomer(
                            currentSource.getCustomerId(), currentTarget.getCustomerId(), currentTarget.getOwner());
                    int movedOpportunities = opportunityRepository.relocateCustomer(
                            currentSource.getCustomerId(), currentTarget.getCustomerId(), currentTarget.getOwner());
                    orderRepository.relocateCustomer(currentSource.getCustomerId(),
                            currentTarget.getCustomerId());
                    log.info("客户合并：source={}→target={}，改挂未完结报价={}，商机={}",
                            currentSource.getCustomerId(), currentTarget.getCustomerId(), movedQuotations,
                            movedOpportunities);
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.CUSTOMER_MERGE, CrmResourceType.CUSTOMER,
                            currentSource.getCustomerId(), mergedSource.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return new CustomerMergeResult(mergedSource, currentTarget,
                            contactRepository.listByCustomer(currentTarget.getCustomerId(), null,
                                    Integer.MAX_VALUE).getItems());
                }, () -> {
                    // 重放：按当前已提交事实重建（target 现有联系人=迁移集+既有集合的超集，
                    // 快照类明细不追加迁移痕迹表，治理回放以当前事实为准）
                    Customer committedSource = customerRepository.findById(command.getSourceCustomerId())
                            .orElseThrow(() -> new CrmException(
                                    CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "customer"));
                    Customer committedTarget = customerRepository.findById(command.getTargetCustomerId())
                            .orElseThrow(() -> new CrmException(
                                    CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "customer"));
                    return new CustomerMergeResult(committedSource, committedTarget,
                            contactRepository.listByCustomer(command.getTargetCustomerId(), null,
                                    Integer.MAX_VALUE).getItems());
                });
    }

    /**
     * 转移客户归属（连带未完结报价；已确认历史不追改）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 转移事实集合
     */
    @Transactional
    public CustomerTransferResult transferCustomer(CrmOwner owner, CrmCommandMetadata metadata,
                                                   TransferCustomerCommand command) {
        Customer customer = customerRepository.findById(command.getCustomerId())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "customer"));
        // 治理动作：跨 owner 归属交接是动作语义本身，准入由 crm.customer.transfer 专属码控制。
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.TRANSFER_CUSTOMER, CrmResourceType.CUSTOMER,
                customer.getCustomerId(), digest(metadata, command), () -> {
                    Customer transferred = customer.transferredTo(command.getNewOwner(),
                            clock.now());
                    customerRepository.update(transferred, customer.getAggregateVersion());
                    List<Quotation> openQuotations = quotationRepository.findOpenByCustomer(
                            customer.getCustomerId());
                    List<Quotation> transferredQuotations = new ArrayList<Quotation>();
                    for (Quotation quotation : openQuotations) {
                        quotationRepository.updateOwner(quotation, command.getNewOwner());
                        transferredQuotations.add(quotation.transferredTo(command.getNewOwner(),
                                clock.now()));
                    }
                    for (Opportunity opportunity : opportunityRepository.findOpenByCustomer(
                            customer.getCustomerId())) {
                        opportunityRepository.update(opportunity.transferredTo(command.getNewOwner(),
                                clock.now()), opportunity.getAggregateVersion());
                    }
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.CUSTOMER_TRANSFER, CrmResourceType.CUSTOMER,
                            customer.getCustomerId(), transferred.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return new CustomerTransferResult(transferred, transferredQuotations);
                }, () -> {
                    // 重放：按当前已提交事实重建（当前未完结集合=转移时刻集合的后续演化）
                    Customer committed = customerRepository.findById(customer.getCustomerId())
                            .orElseThrow(() -> new CrmException(
                                    CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "customer"));
                    return new CustomerTransferResult(committed,
                            quotationRepository.findOpenByCustomer(customer.getCustomerId()));
                });
    }

    /**
     * 转移未完结报价归属。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 转移后报价
     */
    @Transactional
    public Quotation transferQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                       TransferQuotationCommand command) {
        return transferQuotationResult(owner, metadata, command).getQuotation();
    }

    /**
     * 转移报价并冻结首次 HTTP 展示态；Core 门面仍返回既定的 Quotation。
     *
     * @param owner 操作主体
     * @param metadata 命令元数据
     * @param command 转移命令
     * @return 包含首次展示态的转移结果
     */
    @Transactional
    public SimpleCrmServerQuotationTransferResult transferQuotationResult(
            CrmOwner owner, CrmCommandMetadata metadata, TransferQuotationCommand command) {
        Quotation quotation = loadQuotation(command.getQuotationId());
        QuotationVersion quotationVersion = quotationRepository.findVersion(
                quotation.getQuotationId(), quotation.getCurrentVersion())
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotationVersion"));
        // 治理动作：跨 owner 归属交接是动作语义本身，准入由 crm.quotation.transfer 专属码控制。
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.TRANSFER_QUOTATION, CrmResourceType.QUOTATION,
                quotation.getQuotationId(), digest(metadata, command), () -> {
                    if (quotation.isExpired(quotationVersion, clock.now())) {
                        throw new CrmException(CrmErrorCode.QUOTATION_EXPIRED,
                                "expired quotation cannot transfer");
                    }
                    Quotation transferred = quotation.transferredTo(command.getNewOwner(),
                            clock.now());
                    quotationRepository.update(transferred, quotation.getAggregateVersion());
                    if (quotation.getOpportunityId() != null) {
                        Opportunity opportunity = opportunityRepository.findById(quotation.getOpportunityId())
                                .orElseThrow(() -> new CrmException(
                                        CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity"));
                        if (opportunity.getStage() == OpportunityStage.WON
                                || opportunity.getStage() == OpportunityStage.LOST) {
                            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION,
                                    "terminal opportunity quotation cannot transfer");
                        }
                        if (!opportunity.getOwner().equals(command.getNewOwner())) {
                            Opportunity transferredOpportunity = opportunity.transferredTo(
                                    command.getNewOwner(), clock.now());
                            opportunityRepository.update(transferredOpportunity,
                                    opportunity.getAggregateVersion());
                            auditPort.record(owner, CrmAction.OPPORTUNITY_TRANSFER,
                                    CrmResourceType.OPPORTUNITY, opportunity.getOpportunityId(),
                                    transferredOpportunity.getAggregateVersion(),
                                    metadata.getCorrelationId(), null, clock.now());
                        }
                    }
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.QUOTATION_TRANSFER, CrmResourceType.QUOTATION,
                            quotation.getQuotationId(), transferred.getAggregateVersion(),
                            metadata.getCorrelationId(), null, clock.now());
                    return quotationTransferResult(transferred);
                }, () -> quotationTransferResult(loadQuotation(quotation.getQuotationId())));
    }

    private SimpleCrmServerQuotationTransferResult quotationTransferResult(
            Quotation quotation) {
        QuotationVersion version = quotationRepository.findVersion(quotation.getQuotationId(),
                quotation.getCurrentVersion()).orElseThrow(() -> new CrmException(
                CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "quotationVersion"));
        String state = version.getState().getCode();
        if (version.getState() == QuotationState.ISSUED
                && !clock.now().isBefore(version.getValidUntil())) {
            state = QuotationState.EXPIRED.getCode();
        }
        return new SimpleCrmServerQuotationTransferResult(quotation, state);
    }

    /**
     * 回写履约结果：序号严格递增；首次 in_progress 即认领（仓储状态+版本双条件写原子化）。
     *
     * @param reporter 回写主体（消费者 aksk:/人工 iam:）
     * @param metadata 命令元数据
     * @param command 命令
     * @return 推进后履约项
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public FulfillmentItem reportFulfillmentResult(CrmOwner reporter, CrmCommandMetadata metadata,
                                                   ReportFulfillmentResultCommand command) {
        FulfillmentItem item = lockFulfillmentItem(command.getFulfillmentId());
        requireFulfillmentAccess(reporter, item);
        FulfillmentState expectedState = item.getState();
        return replayableIdempotency.execute(reporter, metadata,
                CrmCommandType.REPORT_FULFILLMENT_RESULT, CrmResourceType.FULFILLMENT_ITEM,
                item.getFulfillmentId(), digest(metadata, command), () -> {
                    FulfillmentResult result = new FulfillmentResult(command.getSequence(),
                            reporter.getOwner(), clock.now(), command.getResultData(),
                            command.getNote());
                    FulfillmentItem advanced = item.report(command.getSequence(),
                            FulfillmentState.fromCode(command.getTargetState()), result);
                    fulfillmentItemRepository.transition(advanced, item.getVersion(),
                            expectedState);
                    // 订单展示态按本单交付项事实同事务重算（全部完成=fulfilled、部分=partially_fulfilled）
                    refreshOrderDisplayState(advanced.getOrderId());
                    log.info("履约回写完成：fulfillmentId={}，{}→{}，序号={}", item.getFulfillmentId(),
                            expectedState.getCode(), advanced.getState().getCode(),
                            command.getSequence());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(reporter, CrmAction.FULFILLMENT_REPORT,
                            CrmResourceType.FULFILLMENT_ITEM, item.getFulfillmentId(),
                            advanced.getVersion(), metadata.getCorrelationId(), null,
                            clock.now());
                    return advanced;
                }, () -> {
                    return fulfillmentItemRepository.findByIdForUpdate(command.getFulfillmentId())
                            .orElseThrow(() -> new CrmException(
                                    CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "fulfillmentItem"));
                });
    }

    /**
     * 人工重置失败履约项（水位保留）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据
     * @param command 命令
     * @return 重置后履约项
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public FulfillmentItem resetFulfillmentItem(CrmOwner owner, CrmCommandMetadata metadata,
                                                ResetFulfillmentItemCommand command) {
        FulfillmentItem item = lockFulfillmentItem(command.getFulfillmentId());
        requireOwnership(owner, item.getOwner(), item.getFulfillmentId(), "fulfillment-owner");
        return replayableIdempotency.execute(owner, metadata,
                CrmCommandType.RESET_FULFILLMENT_ITEM, CrmResourceType.FULFILLMENT_ITEM,
                item.getFulfillmentId(), digest(metadata, command), () -> {
                    FulfillmentItem reset = item.reset();
                    fulfillmentItemRepository.transition(reset, item.getVersion(),
                            item.getState());
                    // 展示态随交付项事实同事务回退重算
                    refreshOrderDisplayState(reset.getOrderId());
                    log.info("履约重置：fulfillmentId={}，{}→{}，水位保留={}", item.getFulfillmentId(),
                            item.getState().getCode(), reset.getState().getCode(),
                            reset.getReportSequence());
                    // 审计在首执行闭包内：重放不重复写审计
                    auditPort.record(owner, CrmAction.FULFILLMENT_RESET,
                            CrmResourceType.FULFILLMENT_ITEM, item.getFulfillmentId(),
                            reset.getVersion(), metadata.getCorrelationId(), null, clock.now());
                    return reset;
                }, () -> {
                    return fulfillmentItemRepository.findByIdForUpdate(command.getFulfillmentId())
                            .orElseThrow(() -> new CrmException(
                                    CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "fulfillmentItem"));
                });
    }

    /**
     * 注册或更新履约消费者（consumerId 须与其 AKP clientId 一致——由 controller 校验）。
     *
     * @param owner 归属主体
     * @param metadata 命令元数据（幂等键+关联 ID）
     * @param consumer 消费者事实
     */
    @Transactional
    public void upsertFulfillmentConsumer(CrmOwner owner, CrmCommandMetadata metadata,
                                          io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment
                                                  .FulfillmentConsumer consumer) {
        replayableIdempotency.execute(owner, metadata,
                CrmCommandType.UPSERT_FULFILLMENT_CONSUMER, CrmResourceType.FULFILLMENT_CONSUMER,
                consumer.getConsumerId(), digest(metadata, consumer), () -> {
                    consumerRepository.upsert(consumer);
                    // 审计在首执行闭包内：重放不重复写审计；关联 ID 用命令元数据而非固定值
                    auditPort.record(owner, CrmAction.FULFILLMENT_CONSUMER_ADMIN,
                            CrmResourceType.FULFILLMENT_CONSUMER, consumer.getConsumerId(), 1L,
                            metadata.getCorrelationId(), null, clock.now());
                    return null;
                }, () -> null);
    }

    /**
     * 列全部消费者（管理面）。
     *
     * @return 消费者集合
     */
    public List<io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer>
    listFulfillmentConsumers() {
        return consumerRepository.listAll();
    }

    /**
     * 消费者管理面真实页码列表，计数与当页使用同一只读快照。
     *
     * @param request 页码请求
     * @return 当页数据和总数
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage<
            io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer>
    listFulfillmentConsumers(io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        return consumerRepository.pageAll(request);
    }

    /**
     * 行命令可重复引用同一商品；商品事实按标识只加载一次，业务行仍完整交给领域服务。
     *
     * @param lines 完整报价行命令
     * @return 无重复商品事实集合
     */
    private List<Offering> loadUniqueOfferings(List<QuotationLineCommand> lines) {
        java.util.Set<String> loadedIds = new java.util.HashSet<String>();
        List<Offering> offerings = new ArrayList<Offering>();
        for (QuotationLineCommand line : lines) {
            if (loadedIds.add(line.getOfferingId())) {
                offerings.add(offeringRepository.findById(line.getOfferingId())
                        .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                                "offering")));
            }
        }
        return offerings;
    }

    /**
     * 预期聚合版本乐观校验：调用方预期≠当前事实=409（旧页面/并发命令不得操作最新报价）。
     */
    private void requireExpectedAggregateVersion(long expected, Quotation quotation) {
        if (expected != quotation.getAggregateVersion()) {
            log.warn("报价聚合版本冲突：expected={}，actual={}，quotationId={}", expected,
                    quotation.getAggregateVersion(), quotation.getQuotationId());
            throw new CrmException(CrmErrorCode.AGGREGATE_VERSION_CONFLICT, "quotation");
        }
    }

    /**
     * 写路径归属校验：人员主体只能操作本人事实；机器主体按派发归属（履约项）。
     * 主管/合并/转移等治理动作的跨人操作由 API 码与角色控制（DATA 团队维度随投影扩展）。
     */
    private void requireOwnership(CrmOwner actor, String resourceOwner, String resourceId,
                                  String resourceType) {
        if (actor.getOwner().equals(resourceOwner)) {
            return;
        }
        log.warn("写命令归属拒绝：resourceType={}，resourceId={}", resourceType, resourceId);
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                resourceType);
    }

    /**
     * 先获取订单锁，再读取交付项当前事实，统一同单写路径的锁顺序。
     *
     * @param fulfillmentId 交付项标识
     * @return 当前交付项
     */
    private FulfillmentItem lockFulfillmentItem(String fulfillmentId) {
        FulfillmentItem reference = fulfillmentItemRepository.findById(fulfillmentId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "fulfillmentItem"));
        // 初读仅定位不可变的订单归属；锁后当前读避免使用等待订单锁前的旧快照。
        orderRepository.lockById(reference.getOrderId());
        return fulfillmentItemRepository.findByIdForUpdate(fulfillmentId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "fulfillmentItem"));
    }

    /**
     * 按订单交付项事实重算展示态并条件写入（同事务；状态未变化不写、不加版本噪声）。
     * 仅首执行校正派生态；幂等重放不隐式修复历史偏差，不重复领域动作或业务审计。
     *
     * @param orderId 订单标识
     */
    private void refreshOrderDisplayState(String orderId) {
        List<io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem> items =
                fulfillmentItemRepository.findByOrderIdForUpdate(orderId);
        long completed = 0;
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem item
                : items) {
            if (item.getState() == io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment
                    .FulfillmentState.COMPLETED) {
                completed++;
            }
        }
        io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState derived =
                io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState.derive(
                        items.size(), completed);
        int updated = orderRepository.updateDisplayStateIfChanged(orderId, derived, clock.now());
        if (updated > 0) {
            log.info("订单展示态重算：orderId={}，completed={}/{}，derived={}", orderId, completed,
                    items.size(), derived.getCode());
        }
    }

    /**
     * 履约项写路径双轨：人员=owner 维度（本人事实）；机器消费者=派发归属（consumerId 与 clientId 一致）。
     */
    private void requireFulfillmentAccess(CrmOwner actor,
            io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem item) {
        if (actor.isService()) {
            String clientId = actor.getOwner().substring(
                    io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner.PREFIX_SERVICE.length());
            if (clientId.equals(item.getConsumerId())) {
                return;
            }
        } else if (actor.getOwner().equals(item.getOwner())) {
            return;
        }
        log.warn("履约写路径归属拒绝：fulfillmentId={}，actor={}", item.getFulfillmentId(),
                actor.getOwner());
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                "fulfillmentItem");
    }

    private Quotation loadQuotation(String quotationId) {
        return quotationRepository.findById(quotationId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                        "quotation"));
    }

    private Opportunity loadOpportunity(String opportunityId) {
        return opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, "opportunity"));
    }

    private String digest(CrmCommandMetadata metadata, Object commandPayload) {
        // 摘要=SHA-256(幂等键+命令参数规范化)：同键不同参数必须拒绝（IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD）
        String canonical = SimpleCrmServerJsonCodec.write(java.util.Arrays.asList(
                metadata.getIdempotencyKey(), SimpleCrmServerJsonCodec.write(commandPayload)));
        return sha256Hex(canonical);
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
