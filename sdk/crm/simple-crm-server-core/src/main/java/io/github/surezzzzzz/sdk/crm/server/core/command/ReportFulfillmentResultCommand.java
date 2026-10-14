package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 履约结果回写命令：序号单调递增防乱序，目标态为稳定 code。
 *
 * @author surezzzzzz
 */
@Getter
public final class ReportFulfillmentResultCommand {

    private final String fulfillmentId;
    private final int sequence;
    private final String targetState;
    private final String resultData;
    private final String note;

    /**
     * 创建ReportFulfillmentResultCommand。
     *
     * @param fulfillmentId 履约项唯一标识
     * @param sequence      消费者侧回写序号（必须大于当前水位）
     * @param targetState   目标状态稳定 code（in_progress/completed/failed）
     * @param resultData    结果数据（终态必填，进度可空）
     * @param note          备注（可空）
     */
    public ReportFulfillmentResultCommand(String fulfillmentId, int sequence, String targetState,
                                          String resultData, String note) {
        this.fulfillmentId = CrmValidationHelper.required(fulfillmentId, "fulfillmentId");
        if (sequence < 1) {
            throw CrmException.validation("sequence");
        }
        this.sequence = sequence;
        if (!FulfillmentState.isValid(targetState)
                || FulfillmentState.fromCode(targetState) == FulfillmentState.PENDING_DISPATCH) {
            throw new CrmException(CrmErrorCode.INVALID_FORMAT, "targetState");
        }
        this.targetState = targetState;
        this.resultData = CrmValidationHelper.optional(resultData);
        this.note = CrmValidationHelper.optional(note);
    }
}
