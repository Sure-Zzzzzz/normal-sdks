package io.github.surezzzzzz.sdk.crm.server.core.api;

import io.github.surezzzzzz.sdk.crm.server.core.command.*;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;

/**
 * CRM 首发商业命令门面。
 *
 * <p>入口适配器只能传入已认证的 {@link CrmActor} 和传输无关的 {@link CrmCommandMetadata}；
 * 不得由请求体传入 tenant、操作者、消费者或数据权限。实现必须在同一权威事务内完成
 * 授权、数据范围、幂等、乐观并发、审计和必要 Outbox 事实的写入。</p>
 *
 * <p>创建 Customer、Contact、Offering 和 Quotation 时，运行时适配器必须使用
 * {@link io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmCreateIdempotencyPort}：仅在首次回调中
 * 生成并持久化顶级资源 ID，首次成功和重放后均按该 ID 在当前 tenant 与数据范围内回读结果。报价重放必须以
 * quotationId 回读 Quotation 与初始 QuotationVersion 后重建 QuotationDraft。签发和确认报价的 quotationId
 * 在执行前已经确定，必须使用
 * {@link io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmReplayableIdempotencyPort}：签发按已提交的
 * Quotation 与 QuotationVersion 重建重放结果，确认按已提交的 Quotation、QuotationVersion、Order 与
 * {@link io.github.surezzzzzz.sdk.crm.server.core.port.repository.ReplayableFulfillmentItemRepository} 回读的
 * FulfillmentItem 完整集合重建重放结果。</p>
 *
 * @author surezzzzzz
 */
public interface CrmCommandFacade {

    /**
     * 创建客户领域事实。
     *
     * @param actor    已认证且绑定租户的操作者
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    Customer createCustomer(CrmActor actor, CrmCommandMetadata metadata, CreateCustomerCommand command);

    /**
     * 创建联系人领域事实。
     *
     * @param actor    已认证且绑定租户的操作者
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    Contact createContact(CrmActor actor, CrmCommandMetadata metadata, CreateContactCommand command);

    /**
     * 创建商品或服务领域事实。
     *
     * @param actor    已认证且绑定租户的操作者
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    Offering createOffering(CrmActor actor, CrmCommandMetadata metadata, CreateOfferingCommand command);

    /**
     * 根据已认证操作者和冻结商业事实创建报价草稿。
     *
     * @param actor    已认证且绑定租户的操作者
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    QuotationDraft createQuotation(CrmActor actor, CrmCommandMetadata metadata, CreateQuotationCommand command);

    /**
     * 签发指定报价版本。
     *
     * @param actor    已认证且绑定租户的操作者
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    QuotationIssuance issueQuotation(CrmActor actor, CrmCommandMetadata metadata, IssueQuotationCommand command);

    /**
     * 确认指定报价并生成订单与履约事实。
     *
     * @param actor    已认证且绑定租户的操作者
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的确认报价命令
     * @return 待在同一权威事务持久化的完整确认事实
     */
    QuotationConfirmation confirmQuotation(CrmActor actor, CrmCommandMetadata metadata,
                                           ConfirmQuotationCommand command);
}
