package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.kms.server.model.KmsKeyDestructionDetails;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不包含领取事实原值、内部令牌或密钥材料的销毁明细 HTTP 响应。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KmsKeyDestructionDetailsResponse {
    /**
     * 逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 同一读取快照的逻辑密钥状态。
     */
    private final String keyState;
    /**
     * 同一读取快照的资源版本。
     */
    private final long rowVersion;
    /**
     * 读取时业务取消资格，不能代替 API 或 DATA 授权。
     */
    private final boolean cancelEligible;
    /**
     * 当前排程任务的安全字段。
     */
    private final List<JobResponse> items;

    /**
     * 将内部无材料投影转换为稳定 HTTP 字段和 UTC 毫秒时间。
     *
     * @param details 同一时点的内部明细
     * @return 安全响应
     */
    public static KmsKeyDestructionDetailsResponse fromDetails(KmsKeyDestructionDetails details) {
        List<JobResponse> result = new ArrayList<JobResponse>();
        for (KmsKeyDestructionDetails.Job job : details.getItems()) {
            result.add(new JobResponse(job.getKeyVersion(), job.getState().getCode(), KmsHttpJson.utcMillis(job.getDueAt()),
                    job.getCompletedAt() == null ? null : KmsHttpJson.utcMillis(job.getCompletedAt())));
        }
        return new KmsKeyDestructionDetailsResponse(details.getKeyRef(), details.getKeyState().getCode(),
                details.getRowVersion(), details.isCancelEligible(), Collections.unmodifiableList(result));
    }

    /**
     * 单个版本的排程及实际进度。
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobResponse {
        /**
         * 密钥版本。
         */
        private final int keyVersion;
        /**
         * 当前任务状态编码。
         */
        private final String state;
        /**
         * 最早允许开始的 UTC 毫秒时间。
         */
        private final String dueAt;
        /**
         * 实际完成的 UTC 毫秒时间，未完成时为空。
         */
        private final String completedAt;
    }
}
