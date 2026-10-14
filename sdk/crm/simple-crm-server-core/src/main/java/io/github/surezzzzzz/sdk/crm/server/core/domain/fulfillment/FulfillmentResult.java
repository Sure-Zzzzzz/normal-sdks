package io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 履约结果事实：随回写冻结，终态必带结果数据，进度可只报状态。
 *
 * @author surezzzzzz
 */
@Getter
public final class FulfillmentResult {

    private final int sequence;
    private final String reportedBy;
    private final Instant reportedAt;
    private final String resultData;
    private final String note;

    /**
     * 创建FulfillmentResult。
     *
     * @param sequence   回写序号（消费者侧单调递增）
     * @param reportedBy 回写人 owner（iam:/aksk: 前缀）
     * @param reportedAt 回写时间
     * @param resultData 结果数据（终态必填）
     * @param note       备注（可空）
     */
    public FulfillmentResult(int sequence, String reportedBy, Instant reportedAt, String resultData, String note) {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence");
        }
        this.sequence = sequence;
        this.reportedBy = CrmValidationHelper.required(reportedBy, "reportedBy");
        this.reportedAt = CrmValidationHelper.requiredObject(reportedAt, "reportedAt");
        this.resultData = CrmValidationHelper.optional(resultData);
        this.note = CrmValidationHelper.optional(note);
    }
}
