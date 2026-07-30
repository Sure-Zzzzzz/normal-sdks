package io.github.surezzzzzz.sdk.crm.server.core.api;

import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;

/**
 * CRM 首发权威查询门面。
 *
 * <p>实现必须先应用 tenant 与数据权限条件；不可见资源使用安全 not-found 语义。</p>
 *
 * @author surezzzzzz
 */
public interface CrmQueryFacade {

    /**
     * 按授权租户查询客户。
     *
     * @param actor      已认证且绑定租户的操作者
     * @param customerId 客户唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Customer findCustomer(CrmActor actor, String customerId);

    /**
     * 按授权租户查询联系人。
     *
     * @param actor     已认证且绑定租户的操作者
     * @param contactId 联系人唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Contact findContact(CrmActor actor, String contactId);

    /**
     * 按授权租户查询商品或服务。
     *
     * @param actor      已认证且绑定租户的操作者
     * @param offeringId 商品或服务唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Offering findOffering(CrmActor actor, String offeringId);

    /**
     * 按授权租户查询报价。
     *
     * @param actor       已认证且绑定租户的操作者
     * @param quotationId 报价唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Quotation findQuotation(CrmActor actor, String quotationId);

    /**
     * 按授权租户查询报价版本。
     *
     * @param actor            已认证且绑定租户的操作者
     * @param quotationId      报价唯一标识
     * @param quotationVersion 报价版本事实
     * @return 处理后的领域事实或校验结果。
     */
    QuotationVersion findQuotationVersion(CrmActor actor, String quotationId, int quotationVersion);

    /**
     * 按授权租户查询订单。
     *
     * @param actor   已认证且绑定租户的操作者
     * @param orderId 订单唯一标识
     * @return 处理后的领域事实或校验结果。
     */
    Order findOrder(CrmActor actor, String orderId);
}
