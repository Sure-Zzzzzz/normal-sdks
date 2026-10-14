package io.github.surezzzzzz.sdk.crm.server.core.api;

import io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmAuditFact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;

/**
 * CRM 权威查询门面。
 *
 * <p>实现必须先应用 DATA 行级范围（人=owner 维度、机器消费者=派发归属维度）；不可见资源使用安全
 * not-found 语义；机器续读使用固定排序的游标分页，管理台使用真实页码分页。
 * 页码查询的总数与当页行必须应用相同过滤条件、授权范围和读取快照；两种模式不得混用。</p>
 *
 * @author surezzzzzz
 */
public interface CrmQueryFacade {

    /**
     * 按授权归属范围查询客户。
     *
     * @param actor      归属主体（人 iam:/服务 aksk:）
     * @param customerId 客户唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Customer findCustomer(CrmOwner actor, String customerId);

    /**
     * 按授权归属范围查询联系人。
     *
     * @param actor     归属主体（人 iam:/服务 aksk:）
     * @param contactId 联系人唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Contact findContact(CrmOwner actor, String contactId);

    /**
     * 按入口授权查询商品或服务。
     *
     * @param actor      归属主体（人 iam:/服务 aksk:）
     * @param offeringId 商品或服务唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Offering findOffering(CrmOwner actor, String offeringId);

    /**
     * 按授权归属范围查询报价。
     *
     * @param actor       归属主体（人 iam:/服务 aksk:）
     * @param quotationId 报价唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Quotation findQuotation(CrmOwner actor, String quotationId);

    /**
     * 按授权归属范围查询报价版本。
     *
     * @param actor            归属主体（人 iam:/服务 aksk:）
     * @param quotationId      报价唯一标识
     * @param quotationVersion 报价版本事实
     * @return 处理后的领域事实或校验结果。
     */
    QuotationVersion findQuotationVersion(CrmOwner actor, String quotationId, int quotationVersion);

    /**
     * 按授权归属范围查询订单。
     *
     * @param actor   归属主体（人 iam:/服务 aksk:）
     * @param orderId 订单唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Order findOrder(CrmOwner actor, String orderId);

    /**
     * 按授权范围查询履约项（人=owner 维度、机器消费者=派发归属维度）。
     *
     * @param actor         归属主体（人 iam:/服务 aksk:）
     * @param fulfillmentId 履约项唯一标识
     * @return 处理后的领域事实或安全 not-found
     */
    FulfillmentItem findFulfillmentItem(CrmOwner actor, String fulfillmentId);

    /**
     * 列表查询客户（固定排序游标分页，行级范围由实现并入 SQL）。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<Customer> listCustomers(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit);

    /**
     * 列表查询联系人（criteria.relatedResourceId 为客户 ID）。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<Contact> listContacts(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit);

    /**
     * 列表查询商品或服务（状态过滤含 draft/published/disabled）。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<Offering> listOfferings(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit);

    /**
     * 列表查询报价（状态过滤含惰性 expired 的派生态由实现按 validUntil 表达）。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<Quotation> listQuotations(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit);

    /**
     * 列表查询订单。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<Order> listOrders(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit);

    /**
     * 列表查询履约项（criteria.relatedResourceId 为订单 ID；机器消费者按派发归属过滤）。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<FulfillmentItem> listFulfillmentItems(CrmOwner actor, CrmListCriteria criteria, String cursor,
                                                  int limit);

    /**
     * 查询业务审计事实（授权面由实现控制，通常仅管理授予）。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param criteria 结构化过滤条件（relatedResourceId=资源 ID）
     * @param cursor   不透明游标（首页为 null）
     * @param limit    每页上限
     * @return 当页结果
     */
    CrmPage<CrmAuditFact> listAuditFacts(CrmOwner actor, CrmListCriteria criteria, String cursor, int limit);

    /**
     * 按真实页码查询客户。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与授权范围内总数
     */
    CrmPage<Customer> pageCustomers(CrmOwner actor, CrmListCriteria criteria, CrmPageRequest request);

    /**
     * 按真实页码查询联系人，可见范围随其客户归属判定。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件，relatedResourceId 为客户 ID
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与授权范围内总数
     */
    CrmPage<Contact> pageContacts(CrmOwner actor, CrmListCriteria criteria, CrmPageRequest request);

    /**
     * 按真实页码查询商品或服务。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与总数
     */
    CrmPage<Offering> pageOfferings(CrmOwner actor, CrmListCriteria criteria, CrmPageRequest request);

    /**
     * 按真实页码查询报价，过期过滤按已签发版本的有效截止时间派生。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与授权范围内总数
     */
    CrmPage<Quotation> pageQuotations(CrmOwner actor, CrmListCriteria criteria, CrmPageRequest request);

    /**
     * 按真实页码查询订单。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与授权范围内总数
     */
    CrmPage<Order> pageOrders(CrmOwner actor, CrmListCriteria criteria, CrmPageRequest request);

    /**
     * 按真实页码查询履约项；人员按归属范围，机器按冻结的派发消费者范围。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件，relatedResourceId 为订单 ID
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与授权范围内总数
     */
    CrmPage<FulfillmentItem> pageFulfillmentItems(CrmOwner actor, CrmListCriteria criteria,
                                                  CrmPageRequest request);

    /**
     * 按真实页码查询业务审计事实，查询授权由入口控制。
     *
     * @param actor    已验证归属主体
     * @param criteria 结构化过滤条件，relatedResourceId 为目标资源 ID
     * @param request  页码与每页条数
     * @return 当页行、页码、每页条数与总数
     */
    CrmPage<CrmAuditFact> pageAuditFacts(CrmOwner actor, CrmListCriteria criteria, CrmPageRequest request);
}
