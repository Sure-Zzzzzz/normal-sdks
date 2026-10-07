package io.github.surezzzzzz.sdk.kms.server.model;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 同一数据库读取中的无材料密钥销毁明细。
 *
 * @author surezzzzzz
 */
@Getter
public class KmsKeyDestructionDetails {

    /**
     * 逻辑密钥标识。
     */
    private final String keyRef;
    /**
     * 同一快照中的逻辑密钥状态。
     */
    private final KmsKeyState keyState;
    /**
     * 同一快照中的逻辑密钥版本。
     */
    private final long rowVersion;
    /**
     * 读取时业务上可以取消，不代表已授予销毁操作权限。
     */
    private final boolean cancelEligible;
    /**
     * 当前排程及其已完成任务，按版本升序。
     */
    private final List<Job> items;

    /**
     * 建立不可变的无材料投影。
     *
     * @param keyRef         逻辑密钥标识
     * @param keyState       逻辑密钥状态
     * @param rowVersion     逻辑密钥版本
     * @param cancelEligible 读取时业务取消资格
     * @param items          当前排程任务
     */
    public KmsKeyDestructionDetails(String keyRef, KmsKeyState keyState, long rowVersion,
                                    boolean cancelEligible, List<Job> items) {
        this.keyRef = keyRef;
        this.keyState = keyState;
        this.rowVersion = rowVersion;
        this.cancelEligible = cancelEligible;
        this.items = Collections.unmodifiableList(new ArrayList<Job>(items));
    }

    /**
     * 单个版本的安全任务投影。
     */
    @Getter
    @AllArgsConstructor
    public static class Job {
        /**
         * 密钥版本号。
         */
        private final int keyVersion;
        /**
         * 当前任务状态。
         */
        private final KmsDestructionJobState state;
        /**
         * 允许开始执行的最早时间。
         */
        private final Instant dueAt;
        /**
         * 实际完成时间，未完成时为空。
         */
        private final Instant completedAt;
    }
}
