package io.github.surezzzzzz.sdk.crm.server.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FixedPriceCommercialTermsSnapshot;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationWithdrawal;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerMergeResult;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerTransferResult;

import java.util.List;

/**
 * 已知目标命令首结果的私有编解码器；类型由命令白名单决定，JSON 不携带可执行类名。
 * 使用领域构造器恢复不可变事实，保持 Core 不依赖 Jackson。
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerIdempotencyResultCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
            .registerModule(new SimpleModule().addAbstractTypeMapping(
                    CommercialTermsSnapshot.class, FixedPriceCommercialTermsSnapshot.class))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .addMixIn(Quotation.class, QuotationConstructor.class)
            .addMixIn(QuotationConfirmation.class, ConfirmationConstructor.class);

    private SimpleCrmServerIdempotencyResultCodec() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 保存首结果，空结果以 JSON null 保存，与未保存快照区分。
     *
     * @param result 首次命令结果
     * @return 首结果 JSON
     */
    public static String write(Object result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("idempotency result write failed", e);
        }
    }

    /**
     * 按固定命令类型恢复首结果，不使用应用 ObjectMapper 或默认多态反序列化。
     *
     * @param json 持久化首结果
     * @param commandType 已知目标命令
     * @param <T> 命令声明结果类型
     * @return 首次成功结果
     */
    @SuppressWarnings("unchecked")
    public static <T> T read(String json, CrmCommandType commandType) {
        Class<?> type;
        switch (commandType) {
            case EDIT_QUOTATION:
                type = QuotationDraft.class;
                break;
            case ISSUE_QUOTATION:
                type = QuotationIssuance.class;
                break;
            case CONFIRM_QUOTATION:
                type = QuotationConfirmation.class;
                break;
            case WITHDRAW_QUOTATION:
                type = QuotationWithdrawal.class;
                break;
            case DEACTIVATE_CUSTOMER:
            case ACTIVATE_CUSTOMER:
                type = Customer.class;
                break;
            case PUBLISH_OFFERING:
            case DISABLE_OFFERING:
                type = Offering.class;
                break;
            case MERGE_CUSTOMER:
                type = CustomerMergeResult.class;
                break;
            case TRANSFER_CUSTOMER:
                type = CustomerTransferResult.class;
                break;
            case TRANSFER_QUOTATION:
                type = SimpleCrmServerQuotationTransferResult.class;
                break;
            case TRANSITION_OPPORTUNITY:
            case TRANSFER_OPPORTUNITY:
                type = Opportunity.class;
                break;
            case REPORT_FULFILLMENT_RESULT:
            case RESET_FULFILLMENT_ITEM:
                type = FulfillmentItem.class;
                break;
            case UPSERT_FULFILLMENT_CONSUMER:
                type = Void.class;
                break;
            default:
                throw new IllegalArgumentException("commandType");
        }
        try {
            return (T) MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("idempotency result read failed", e);
        }
    }

    /** 商机关联使 Quotation 出现重载构造器，显式声明快照恢复所用的完整构造器。 */
    private abstract static class QuotationConstructor {

        @JsonCreator
        QuotationConstructor(@JsonProperty("quotationId") String quotationId,
                             @JsonProperty("customerId") String customerId,
                             @JsonProperty("opportunityId") String opportunityId,
                             @JsonProperty("owner") String owner,
                             @JsonProperty("aggregateVersion") long aggregateVersion,
                             @JsonProperty("currentVersion") int currentVersion,
                             @JsonProperty("currentConfirmableVersion") Integer currentConfirmableVersion,
                             @JsonProperty("confirmedOrderId") String confirmedOrderId,
                             @JsonProperty("supersedesQuotationId") String supersedesQuotationId,
                             @JsonProperty("createdAt") java.time.Instant createdAt,
                             @JsonProperty("updatedAt") java.time.Instant updatedAt) {
        }
    }

    /** 多构造器结果显式选择包含取代事实的完整构造器。 */
    private abstract static class ConfirmationConstructor {

        @JsonCreator
        ConfirmationConstructor(@JsonProperty("quotation") Quotation quotation,
                                @JsonProperty("quotationVersion") QuotationVersion quotationVersion,
                                @JsonProperty("order") Order order,
                                @JsonProperty("fulfillmentItems") List<FulfillmentItem> fulfillmentItems,
                                @JsonProperty("supersededQuotation") Quotation supersededQuotation,
                                @JsonProperty("supersededQuotationVersion") QuotationVersion supersededQuotationVersion) {
        }
    }
}
