package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmAuditFact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcAuditRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcContactRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcCustomerRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcFulfillmentItemRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOfferingRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOpportunityRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcOrderRepository;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcQuotationRepository;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerCursorHelper;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerOwnerScope;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerTimeHelper;

import java.util.List;
import java.util.Map;

/**
 * CRM 查询服务：owner 维度（人员）与派发归属维度（消费者机器）双轨；
 * 不可见一律安全 not-found；游标为 Base64 行主键。
 *
 * <p>行级范围的最小实现为 owner 等值（团队 IN 随 IAM DATA 投影就位由 controller 扩展传入）。</p>
 *
 * @author surezzzzzz
 */
public class SimpleCrmServerQueryService {

    private final SimpleCrmServerJdbcCustomerRepository customerRepository;
    private final SimpleCrmServerJdbcContactRepository contactRepository;
    private final SimpleCrmServerJdbcOfferingRepository offeringRepository;
    private final SimpleCrmServerJdbcOpportunityRepository opportunityRepository;
    private final SimpleCrmServerJdbcQuotationRepository quotationRepository;
    private final SimpleCrmServerJdbcOrderRepository orderRepository;
    private final SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository;
    private final SimpleCrmServerJdbcAuditRepository auditRepository;
    private final io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmClock clock;

    /**
     * 创建查询服务。
     *
     * @param customerRepository 客户仓储
     * @param contactRepository 联系人仓储
     * @param offeringRepository 目录仓储
     * @param quotationRepository 报价仓储
     * @param orderRepository 订单仓储
     * @param fulfillmentItemRepository 履约项仓储
     * @param auditRepository 审计仓储
     */
    public SimpleCrmServerQueryService(SimpleCrmServerJdbcCustomerRepository customerRepository,
                           SimpleCrmServerJdbcContactRepository contactRepository,
                           SimpleCrmServerJdbcOfferingRepository offeringRepository,
                           SimpleCrmServerJdbcOpportunityRepository opportunityRepository,
                           SimpleCrmServerJdbcQuotationRepository quotationRepository,
                           SimpleCrmServerJdbcOrderRepository orderRepository,
                           SimpleCrmServerJdbcFulfillmentItemRepository fulfillmentItemRepository,
                           SimpleCrmServerJdbcAuditRepository auditRepository,
                           io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmClock clock) {
        this.customerRepository = customerRepository;
        this.contactRepository = contactRepository;
        this.offeringRepository = offeringRepository;
        this.opportunityRepository = opportunityRepository;
        this.quotationRepository = quotationRepository;
        this.orderRepository = orderRepository;
        this.fulfillmentItemRepository = fulfillmentItemRepository;
        this.auditRepository = auditRepository;
        this.clock = () -> SimpleCrmServerTimeHelper.truncateToMillis(clock.now());
    }

    /**
     * 点查客户（不可见=安全 404）。
     *
     * @param actor 归属主体
     * @param customerId 客户标识
     * @return 客户事实
     */
    public Customer findCustomer(CrmOwner actor, DataAccessPlan plan, String customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> notFound("customer"));
        requireVisible(SimpleCrmServerOwnerScope.of(actor, plan), customer.getOwner());
        return customer;
    }

    /**
     * 点查联系人。
     *
     * @param actor 归属主体
     * @param contactId 联系人标识
     * @return 联系人事实
     */
    public Contact findContact(CrmOwner actor, DataAccessPlan plan, String contactId) {
        Contact contact = contactRepository.findById(contactId).orElseThrow(() -> notFound("contact"));
        // 联系人随客户归属判定可见性（联系人行本身不携带 owner 列）
        Customer customer = customerRepository.findById(contact.getCustomerId())
                .orElseThrow(() -> notFound("customer"));
        requireVisible(SimpleCrmServerOwnerScope.of(actor, plan), customer.getOwner());
        return contact;
    }

    /**
     * 点查目录（读对全体持码者开放）。
     *
     * @param actor 归属主体
     * @param offeringId 目录标识
     * @return 目录事实
     */
    public Offering findOffering(CrmOwner actor, String offeringId) {
        return offeringRepository.findById(offeringId).orElseThrow(() -> notFound("offering"));
    }

    /**
     * 点查报价。
     *
     * @param actor 归属主体
     * @param quotationId 报价标识
     * @return 报价事实
     */
    public Quotation findQuotation(CrmOwner actor, DataAccessPlan plan, String quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> notFound("quotation"));
        requireVisible(SimpleCrmServerOwnerScope.of(actor, plan), quotation.getOwner());
        return quotation;
    }

    /** 点查商机，按客户 owner DATA 范围执行。 */
    public Opportunity findOpportunity(CrmOwner actor, DataAccessPlan plan, String opportunityId) {
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> notFound("opportunity"));
        requireVisible(SimpleCrmServerOwnerScope.of(actor, plan), opportunity.getOwner());
        return opportunity;
    }

    /** 商机列表，页码与游标模式共用同一 SQL 过滤和 DATA 范围。 */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<Opportunity> listOpportunities(CrmOwner actor, DataAccessPlan plan,
                                                   CrmListCriteria criteria, String cursor, int limit,
                                                   io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("opportunities", actor, criteria,
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners());
        SimpleCrmServerPaged<Opportunity> paged = opportunityRepository.listByOwner(
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners(), criteria.getStateCode(),
                criteria.getRelatedResourceId(), criteria.getKeyword(),
                SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 点查报价版本。
     *
     * @param actor 归属主体
     * @param quotationId 报价标识
     * @param version 版本号
     * @return 版本事实
     */
    public io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion
    findQuotationVersion(CrmOwner actor, DataAccessPlan plan, String quotationId, int version) {
        findQuotation(actor, plan, quotationId);
        return quotationRepository.findVersion(quotationId, version)
                .orElseThrow(() -> notFound("quotationVersion"));
    }

    /**
     * 点查订单。
     *
     * @param actor 归属主体
     * @param orderId 订单标识
     * @return 订单事实
     */
    public Order findOrder(CrmOwner actor, DataAccessPlan plan, String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> notFound("order"));
        requireVisible(SimpleCrmServerOwnerScope.of(actor, plan), order.getOwner());
        return order;
    }

    /**
     * 点查履约项（人=owner 维度、机器=派发归属维度）。
     *
     * @param actor 归属主体
     * @param fulfillmentId 履约项标识
     * @return 履约项事实
     */
    public FulfillmentItem findFulfillmentItem(CrmOwner actor, DataAccessPlan plan,
                                                String fulfillmentId) {
        FulfillmentItem item = fulfillmentItemRepository.findById(fulfillmentId)
                .orElseThrow(() -> notFound("fulfillmentItem"));
        // 点查可见性=派发归属（机器）或 DATA 范围（人：等值兜底∪授权范围，与列表同口径）
        boolean visible = actor.isService()
                ? item.getConsumerId().equals(actor.getOwner().substring(
                        io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner.PREFIX_SERVICE
                                .length()))
                : SimpleCrmServerOwnerScope.of(actor, plan).allows(item.getOwner());
        if (!visible) {
            throw notFound("fulfillmentItem");
        }
        return item;
    }

    /**
     * 客户列表（owner 范围）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    public CrmPage<Customer> listCustomers(CrmOwner actor, DataAccessPlan plan,
                                           CrmListCriteria criteria, String cursor, int limit) {
        return listCustomers(actor, plan, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param plan 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<Customer> listCustomers(CrmOwner actor, DataAccessPlan plan,
                                           CrmListCriteria criteria, String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("customers", actor, criteria,
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners());
        SimpleCrmServerPaged<Customer> paged = customerRepository.listByOwner(
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners(), criteria.getStateCode(),
                criteria.getKeyword(), SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 联系人列表（按客户）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件（relatedResourceId=客户 ID）
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    public CrmPage<Contact> listContacts(CrmOwner actor, DataAccessPlan plan,
                                         CrmListCriteria criteria, String cursor, int limit) {
        return listContacts(actor, plan, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param plan 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<Contact> listContacts(CrmOwner actor, DataAccessPlan plan,
                                         CrmListCriteria criteria, String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("contacts", actor, criteria,
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners());
        String customerId = criteria.getRelatedResourceId();
        if (customerId == null) {
            throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation(
                    "relatedCustomerId");
        }
        // 联系人可见性随客户归属（本人等值或 DATA 授权展开）
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> notFound("customer"));
        requireVisible(SimpleCrmServerOwnerScope.of(actor, plan), customer.getOwner());
        SimpleCrmServerPaged<Contact> paged = contactRepository.listByCustomer(customerId,
                SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 目录列表（读无 DATA）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    public CrmPage<Offering> listOfferings(CrmOwner actor, CrmListCriteria criteria, String cursor,
                                           int limit) {
        return listOfferings(actor, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<Offering> listOfferings(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("offerings", actor, criteria,
                null);
        SimpleCrmServerPaged<Offering> paged = offeringRepository.list(criteria.getStateCode(),
                criteria.getKeyword(), SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 报价列表（owner 范围）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    public CrmPage<Quotation> listQuotations(CrmOwner actor, DataAccessPlan plan,
                                             CrmListCriteria criteria, String cursor, int limit) {
        return listQuotations(actor, plan, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param plan 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<Quotation> listQuotations(CrmOwner actor, DataAccessPlan plan,
                                             CrmListCriteria criteria, String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("quotations", actor, criteria,
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners());
        SimpleCrmServerPaged<Quotation> paged = quotationRepository.listByOwner(
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners(), criteria.getStateCode(),
                criteria.getKeyword(), SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 订单列表（owner 范围）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    public CrmPage<Order> listOrders(CrmOwner actor, DataAccessPlan plan, CrmListCriteria criteria,
                                     String cursor, int limit) {
        return listOrders(actor, plan, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param plan 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<Order> listOrders(CrmOwner actor, DataAccessPlan plan, CrmListCriteria criteria,
                                     String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("orders", actor, criteria,
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners());
        SimpleCrmServerPaged<Order> paged = orderRepository.listByOwner(
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners(), criteria.getKeyword(),
                SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 履约项列表（人=owner 维度、机器=派发归属维度）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    public CrmPage<FulfillmentItem> listFulfillmentItems(CrmOwner actor, DataAccessPlan plan,
                                                         CrmListCriteria criteria,
                                                         String cursor, int limit) {
        return listFulfillmentItems(actor, plan, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param plan 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<FulfillmentItem> listFulfillmentItems(CrmOwner actor, DataAccessPlan plan,
                                                         CrmListCriteria criteria,
                                                         String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("fulfillment-items", actor, criteria,
                SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners());
        SimpleCrmServerPaged<FulfillmentItem> paged;
        if (actor.isService()) {
            paged = fulfillmentItemRepository.listByConsumer(
                    actor.getOwner().substring(
                            io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner.PREFIX_SERVICE
                                    .length()),
                    criteria.getStateCode(), SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        } else {
            paged = fulfillmentItemRepository.listByOwner(
                    SimpleCrmServerOwnerScope.of(actor, plan).sqlOwners(), criteria.getStateCode(),
                    criteria.getRelatedResourceId(), SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        }
        return page(paged, limit, request, fingerprint);
    }

    /**
     * 批量取聚合当前版本事实（版本 state+有效截止），供视图派生展示态（终态真相在版本行）。
     *
     * @param quotations 聚合列表
     * @return quotationId → [版本 state code, validUntil]
     */
    public Map<String, Object[]> quotationVersionFacts(
            List<Quotation> quotations) {
        return quotationRepository.findCurrentVersionFacts(quotations);
    }

    /**
     * 派生报价展示态（confirmed/withdrawn/superseded/expired/issued/draft）。
     * 撤回/取代后聚合字段与草稿同形，判定唯一依据=版本行 state；过期=已签发且越过有效截止（惰性）。
     *
     * @param quotation 聚合
     * @param versionFact 版本事实 [state code, validUntil]（可空=按聚合三档退化）
     * @return 展示态稳定 code
     */
    public String deriveDisplayState(Quotation quotation, Object[] versionFact) {
        if (quotation.getConfirmedOrderId() != null) {
            return "confirmed";
        }
        String versionState = versionFact == null ? null : String.valueOf(versionFact[0]);
        if ("withdrawn".equals(versionState)) {
            return "withdrawn";
        }
        if ("superseded".equals(versionState)) {
            return "superseded";
        }
        if (quotation.getCurrentConfirmableVersion() != null) {
            java.time.Instant validUntil = versionFact == null || versionFact[1] == null
                    ? null : (java.time.Instant) versionFact[1];
            return validUntil != null && !clock.now().isBefore(validUntil) ? "expired" : "issued";
        }
        return "draft";
    }

    /**
     * 审计查询（授权由 controller 码控）。
     *
     * @param actor 归属主体
     * @param criteria 过滤条件（relatedResourceId=资源 ID）
     * @param cursor 游标
     * @param limit 每页上限
     * @return 当页
     */
    // 审计查询按设计全量（DATA 不适用）：crm.audit.read 仅 supervisor/admin 角色授予
    public CrmPage<CrmAuditFact> listAuditFacts(CrmOwner actor, CrmListCriteria criteria,
                                                String cursor, int limit) {
        return listAuditFacts(actor, criteria, cursor, limit, null);
    }

    /**
     * 同一过滤和授权范围的页码查询；COUNT 与当页行在同一只读快照内取得。
     *
     * @param actor 查询条件或游标边界
     * @param criteria 查询条件或游标边界
     * @param cursor 查询条件或游标边界
     * @param limit 查询条件或游标边界
     * @param request 页码请求，游标模式为空
     * @return 当页结果
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CrmPage<CrmAuditFact> listAuditFacts(CrmOwner actor, CrmListCriteria criteria,
                                                String cursor, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        String fingerprint = SimpleCrmServerCursorHelper.fingerprint("operation-logs", actor, criteria,
                null);
        SimpleCrmServerPaged<CrmAuditFact> paged = auditRepository.list(criteria.getRelatedResourceId(),
                SimpleCrmServerCursorHelper.decode(cursor, fingerprint), limit, request);
        return page(paged, limit, request, fingerprint);
    }

    private void requireVisible(SimpleCrmServerOwnerScope scope, String owner) {
        if (!scope.allows(owner)) {
            throw notFound("resource");
        }
    }

    private CrmException notFound(String resource) {
        return new CrmException(CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE, resource);
    }

    private <T> CrmPage<T> page(SimpleCrmServerPaged<T> paged, int limit,
            io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request, String fingerprint) {
        if (request != null) {
            return new CrmPage<T>(paged.getItems(), request, paged.getTotal());
        }
        // 仅实际探测到额外行才产生续读游标，整批末页不出现空下一页
        if (paged.isHasNext() && paged.getLastPrimaryKey() != null) {
            return new CrmPage<T>(paged.getItems(),
                    SimpleCrmServerCursorHelper.encode(paged.getLastPrimaryKey(), fingerprint));
        }
        return new CrmPage<T>(paged.getItems(), null);
    }
}
