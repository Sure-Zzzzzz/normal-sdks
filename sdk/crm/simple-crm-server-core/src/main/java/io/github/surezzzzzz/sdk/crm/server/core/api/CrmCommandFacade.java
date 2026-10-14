package io.github.surezzzzzz.sdk.crm.server.core.api;

import io.github.surezzzzzz.sdk.crm.server.core.command.*;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.*;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerMergeResult;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerTransferResult;

/**
 * CRM 首发商业命令门面。
 *
 * <p>入口适配器只能传入已认证的 {@link CrmOwner} 和传输无关的 {@link CrmCommandMetadata}；
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
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    Customer createCustomer(CrmOwner actor, CrmCommandMetadata metadata, CreateCustomerCommand command);

    /**
     * 创建联系人领域事实。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    Contact createContact(CrmOwner actor, CrmCommandMetadata metadata, CreateContactCommand command);

    /**
     * 创建商品或服务领域事实。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    Offering createOffering(CrmOwner actor, CrmCommandMetadata metadata, CreateOfferingCommand command);

    /**
     * 根据已认证操作者和冻结商业事实创建报价草稿。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    QuotationDraft createQuotation(CrmOwner actor, CrmCommandMetadata metadata, CreateQuotationCommand command);

    /**
     * 签发指定报价版本。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 处理后的领域事实或校验结果。
     */
    QuotationIssuance issueQuotation(CrmOwner actor, CrmCommandMetadata metadata, IssueQuotationCommand command);

    /**
     * 确认指定报价并生成订单与履约事实。
     *
     * @param actor    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的确认报价命令
     * @return 待在同一权威事务持久化的完整确认事实
     */
    QuotationConfirmation confirmQuotation(CrmOwner actor, CrmCommandMetadata metadata,
                                           ConfirmQuotationCommand command);

    /**
     * 编辑报价草稿（整单行项替换，仅当前草稿版本可用）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 编辑后的草稿事实对
     */
    QuotationDraft editQuotationDraft(CrmOwner owner, CrmCommandMetadata metadata, EditQuotationCommand command);

    /**
     * 撤回已签发报价（同事务聚合与版本置 WITHDRAWN）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 撤回后的事实对
     */
    QuotationWithdrawal withdrawQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                          WithdrawQuotationCommand command);

    /**
     * 重议派生新草稿（自已签发报价复制快照，新报价确认时来源自动 SUPERSEDED）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 以复制快照为起点的新草稿事实对
     */
    QuotationDraft renegotiateQuotation(CrmOwner owner, CrmCommandMetadata metadata,
                                        RenegotiateQuotationCommand command);

    /**
     * 停用客户（不进新报价/新联系人，历史不受影响）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 停用后的客户事实
     */
    Customer deactivateCustomer(CrmOwner owner, CrmCommandMetadata metadata, DeactivateCustomerCommand command);

    /**
     * 复启客户。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 复启后的客户事实
     */
    Customer activateCustomer(CrmOwner owner, CrmCommandMetadata metadata, ActivateCustomerCommand command);

    /**
     * 上架商品或服务（可进新报价）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 上架后的商品或服务事实
     */
    Offering publishOffering(CrmOwner owner, CrmCommandMetadata metadata, PublishOfferingCommand command);

    /**
     * 下架商品或服务（不进新报价，历史不受影响，可重新上架）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 下架后的商品或服务事实
     */
    Offering disableOffering(CrmOwner owner, CrmCommandMetadata metadata, DisableOfferingCommand command);

    /**
     * 合并重复客户：源客户联系人、未完结报价与已确认事实同事务改挂目标，源置 MERGED 终态。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 合并事实集合（源客户终态与改挂清单）
     */
    CustomerMergeResult mergeCustomer(CrmOwner owner, CrmCommandMetadata metadata, MergeCustomerCommand command);

    /**
     * 转移客户归属（连带未完结报价，已确认历史事实不追改）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 转移事实集合（客户与连带报价终态）
     */
    CustomerTransferResult transferCustomer(CrmOwner owner, CrmCommandMetadata metadata,
                                            TransferCustomerCommand command);

    /**
     * 转移未完结报价归属。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 转移后的报价事实
     */
    Quotation transferQuotation(CrmOwner owner, CrmCommandMetadata metadata, TransferQuotationCommand command);

    /**
     * 回写履约结果（序号单调递增；首次回写 in_progress 即认领）。
     *
     * @param owner    回写主体（消费者为 aksk:/人工为 iam:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 推进后的履约项事实
     */
    FulfillmentItem reportFulfillmentResult(CrmOwner owner, CrmCommandMetadata metadata,
                                            ReportFulfillmentResultCommand command);

    /**
     * 人工重置失败履约项回待执行（水位保留）。
     *
     * @param owner    归属主体（人 iam:/服务 aksk:）
     * @param metadata 命令关联与幂等元数据
     * @param command  传输无关的业务命令
     * @return 重置后的履约项事实
     */
    FulfillmentItem resetFulfillmentItem(CrmOwner owner, CrmCommandMetadata metadata,
                                         ResetFulfillmentItemCommand command);
}
