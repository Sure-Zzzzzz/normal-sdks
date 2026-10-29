package io.github.surezzzzzz.sdk.crm.server.constant;

/**
 * Simple CRM Server 全局常量。
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerConstant {

    private SimpleCrmServerConstant() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.crm.server";

    /**
     * 总开关配置键。
     */
    public static final String CONFIG_PROPERTY_ENABLE = "enable";

    /**
     * 布尔真值。
     */
    public static final String BOOLEAN_TRUE = "true";

    /**
     * API 码：客户创建。
     */
    public static final String API_CODE_CUSTOMER_CREATE = "crm.customer.create";

    /**
     * API 码：客户点查。
     */
    public static final String API_CODE_CUSTOMER_READ = "crm.customer.read";

    /**
     * API 码：客户列表。
     */
    public static final String API_CODE_CUSTOMER_LIST = "crm.customer.list";

    /**
     * API 码：客户停用。
     */
    public static final String API_CODE_CUSTOMER_DISABLE = "crm.customer.disable";

    /**
     * API 码：客户复启。
     */
    public static final String API_CODE_CUSTOMER_ACTIVATE = "crm.customer.activate";

    /**
     * API 码：客户合并。
     */
    public static final String API_CODE_CUSTOMER_MERGE = "crm.customer.merge";

    /**
     * API 码：客户归属转移。
     */
    public static final String API_CODE_CUSTOMER_TRANSFER = "crm.customer.transfer";

    /**
     * API 码：联系人创建。
     */
    public static final String API_CODE_CONTACT_CREATE = "crm.contact.create";

    /**
     * API 码：联系人点查。
     */
    public static final String API_CODE_CONTACT_READ = "crm.contact.read";

    /**
     * API 码：联系人列表。
     */
    public static final String API_CODE_CONTACT_LIST = "crm.contact.list";

    /**
     * API 码：商品创建。
     */
    public static final String API_CODE_OFFERING_CREATE = "crm.offering.create";

    /**
     * API 码：商品点查。
     */
    public static final String API_CODE_OFFERING_READ = "crm.offering.read";

    /**
     * API 码：商品列表。
     */
    public static final String API_CODE_OFFERING_LIST = "crm.offering.list";

    /**
     * API 码：商品上架。
     */
    public static final String API_CODE_OFFERING_PUBLISH = "crm.offering.publish";

    /**
     * API 码：商品下架。
     */
    public static final String API_CODE_OFFERING_DISABLE = "crm.offering.disable";

    /**
     * API 码：报价创建。
     */
    public static final String API_CODE_QUOTATION_CREATE = "crm.quotation.create";

    /**
     * API 码：报价点查。
     */
    public static final String API_CODE_QUOTATION_READ = "crm.quotation.read";

    /**
     * API 码：报价列表。
     */
    public static final String API_CODE_QUOTATION_LIST = "crm.quotation.list";

    /**
     * API 码：报价编辑。
     */
    public static final String API_CODE_QUOTATION_EDIT = "crm.quotation.edit";

    /**
     * API 码：报价签发。
     */
    public static final String API_CODE_QUOTATION_ISSUE = "crm.quotation.issue";

    /**
     * API 码：报价确认。
     */
    public static final String API_CODE_QUOTATION_CONFIRM = "crm.quotation.confirm";

    /**
     * API 码：报价撤回。
     */
    public static final String API_CODE_QUOTATION_WITHDRAW = "crm.quotation.withdraw";

    /**
     * API 码：报价重议。
     */
    public static final String API_CODE_QUOTATION_RENEGOTIATE = "crm.quotation.renegotiate";

    /**
     * API 码：报价归属转移。
     */
    public static final String API_CODE_QUOTATION_TRANSFER = "crm.quotation.transfer";

    /** API 码：商机创建。 */
    public static final String API_CODE_OPPORTUNITY_CREATE = "crm.opportunity.create";

    /** API 码：商机点查。 */
    public static final String API_CODE_OPPORTUNITY_READ = "crm.opportunity.read";

    /** API 码：商机列表。 */
    public static final String API_CODE_OPPORTUNITY_LIST = "crm.opportunity.list";

    /** API 码：商机阶段推进。 */
    public static final String API_CODE_OPPORTUNITY_STAGE = "crm.opportunity.stage";

    /** API 码：商机归属转移。 */
    public static final String API_CODE_OPPORTUNITY_TRANSFER = "crm.opportunity.transfer";

    /**
     * API 码：订单点查。
     */
    public static final String API_CODE_ORDER_READ = "crm.order.read";

    /**
     * API 码：订单列表。
     */
    public static final String API_CODE_ORDER_LIST = "crm.order.list";

    /**
     * API 码：履约项点查。
     */
    public static final String API_CODE_FULFILLMENT_READ = "crm.fulfillment.read";

    /**
     * API 码：履约项列表。
     */
    public static final String API_CODE_FULFILLMENT_LIST = "crm.fulfillment.list";

    /**
     * API 码：履约结果回写。
     */
    public static final String API_CODE_FULFILLMENT_REPORT = "crm.fulfillment.report";

    /**
     * API 码：履约项人工重置。
     */
    public static final String API_CODE_FULFILLMENT_RESET = "crm.fulfillment.reset";

    /**
     * API 码：消费者注册管理。
     */
    public static final String API_CODE_FULFILLMENT_CONSUMER_ADMIN = "crm.fulfillment.consumer-admin";

    /**
     * API 码：审计查询。
     */
    public static final String API_CODE_AUDIT_READ = "crm.audit.read";

    /** DATA 资源：客户（owner 维度，报价/订单/履约随客户归属派生）。 */
    public static final String DATA_RESOURCE_CUSTOMER = "crm-customer";

    /** DATA 动作：读。 */
    public static final String DATA_ACTION_READ = "read";

    /**
     * 数据写入动作。
     */
    public static final String DATA_ACTION_WRITE = "write";

    /** DATA 约束维度：归属主体（iam:/aksk: 前缀坐标）。 */
    public static final String DATA_DIMENSION_OWNER = "owner";

    /**
     * 幂等键请求头。
     */
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    /**
     * 关联 ID 请求头。
     */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    /** 幂等占位写入行数异常。 */
    public static final String ERROR_IDEMPOTENCY_CLAIM_COUNT = "幂等占位写入行数异常";

    /** 创建命令的首次资源结果未能保存。 */
    public static final String ERROR_IDEMPOTENCY_CREATE_RESULT = "幂等创建结果未能保存";

    /** 已知目标命令的首次结果未能保存。 */
    public static final String ERROR_IDEMPOTENCY_RESULT = "幂等首次结果未能保存";

    /** 已提交的创建命令缺少首次资源结果。 */
    public static final String ERROR_IDEMPOTENCY_CREATE_RESULT_MISSING = "幂等创建结果缺失";

    /** 唯一键冲突后未能读取已提交的首次记录。 */
    public static final String ERROR_IDEMPOTENCY_RECORD_MISSING = "幂等首次记录缺失";

    /** 幂等执行未加入业务事务。 */
    public static final String ERROR_IDEMPOTENCY_TRANSACTION_REQUIRED = "幂等执行必须处于业务事务中";

}
