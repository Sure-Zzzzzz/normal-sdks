package io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 履约项事实：确认报价时冻结，消费者拉取认领后回写推进状态。
 *
 * <p>状态机：PENDING_DISPATCH →（认领/进度）IN_PROGRESS →（终态）COMPLETED / FAILED；FAILED 可人工重置回
 * PENDING_DISPATCH；COMPLETED 终态不可再报（误报走线下修正，防重复履约）。回写序号必须严格大于当前水位，
 * 防乱序与重复回写；认领语义=首次回写 IN_PROGRESS（仓储以状态+版本条件写实现原子认领）。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class FulfillmentItem {

    private final String fulfillmentId;
    private final String orderId;
    private final String orderLineId;
    private final int version;
    private final FulfillmentState state;
    private final FulfillmentObligationTemplate obligationTemplate;
    private final String consumerId;
    private final int consumerProtocolVersion;
    private final String owner;
    private final int reportSequence;
    private final FulfillmentResult result;

    /**
     * 创建FulfillmentItem。
     *
     * @param fulfillmentId           履约项唯一标识
     * @param orderId                 订单唯一标识
     * @param orderLineId             订单行唯一标识
     * @param version                 聚合版本
     * @param state                   业务状态
     * @param obligationTemplate      冻结的履约义务模板
     * @param consumerId              冻结的消费者标识（须绑定其 AKP clientId）
     * @param consumerProtocolVersion 消费者协议版本
     * @param owner                   归属主体（继承报价 owner，人员查询维度）
     * @param reportSequence          已回写序号水位（初始 0，只增不减）
     * @param result                  最近回写结果（可空）
     */
    public FulfillmentItem(String fulfillmentId, String orderId, String orderLineId,
                           int version, FulfillmentState state,
                           FulfillmentObligationTemplate obligationTemplate, String consumerId,
                           int consumerProtocolVersion, String owner, int reportSequence,
                           FulfillmentResult result) {
        this.fulfillmentId = CrmValidationHelper.required(fulfillmentId, "fulfillmentId");
        this.orderId = CrmValidationHelper.required(orderId, "orderId");
        this.orderLineId = CrmValidationHelper.required(orderLineId, "orderLineId");
        this.version = CrmValidationHelper.positiveVersion(version, "version");
        this.state = CrmValidationHelper.requiredObject(state, "state");
        this.obligationTemplate = CrmValidationHelper.requiredObject(obligationTemplate, "obligationTemplate");
        this.consumerId = CrmValidationHelper.required(consumerId, "consumerId");
        this.consumerProtocolVersion = CrmValidationHelper.positiveVersion(consumerProtocolVersion,
                "consumerProtocolVersion");
        this.owner = CrmValidationHelper.required(owner, "owner");
        if (reportSequence < 0) {
            throw CrmException.validation("reportSequence");
        }
        this.reportSequence = reportSequence;
        this.result = result;
    }

    /**
     * 回写结果：目标态可为 IN_PROGRESS（首次即认领）/COMPLETED/FAILED；序号必须严格大于当前水位。
     *
     * @param sequence 回写序号
     * @param target   目标状态
     * @param result   回写结果事实
     * @return 推进后的履约项事实
     */
    public FulfillmentItem report(int sequence, FulfillmentState target, FulfillmentResult result) {
        CrmValidationHelper.requiredObject(result, "result");
        if (sequence <= reportSequence) {
            throw new CrmException(CrmErrorCode.FULFILLMENT_RESULT_CONSISTENCY_FAILURE,
                    "fulfillment report sequence must increase");
        }
        if (state == FulfillmentState.COMPLETED
                || (state == FulfillmentState.IN_PROGRESS && target == FulfillmentState.PENDING_DISPATCH)) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "fulfillment item is completed");
        }
        if ((state == FulfillmentState.PENDING_DISPATCH || state == FulfillmentState.IN_PROGRESS)
                && (target == FulfillmentState.IN_PROGRESS || target == FulfillmentState.COMPLETED
                || target == FulfillmentState.FAILED)) {
            if (target != FulfillmentState.IN_PROGRESS && result.getResultData() == null) {
                throw CrmException.validation("resultData");
            }
            return new FulfillmentItem(fulfillmentId, orderId, orderLineId, version + 1, target,
                    obligationTemplate, consumerId, consumerProtocolVersion, owner, sequence, result);
        }
        throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "fulfillment state transition is illegal");
    }

    /**
     * 人工重置：仅 FAILED 可重置回 PENDING_DISPATCH 重新拉取执行；水位保留（拒绝旧序号回写）。
     *
     * @return 重置后的履约项事实
     */
    public FulfillmentItem reset() {
        if (state != FulfillmentState.FAILED) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "only failed item can be reset");
        }
        return new FulfillmentItem(fulfillmentId, orderId, orderLineId, version + 1,
                FulfillmentState.PENDING_DISPATCH, obligationTemplate, consumerId, consumerProtocolVersion,
                owner, reportSequence, null);
    }
}
