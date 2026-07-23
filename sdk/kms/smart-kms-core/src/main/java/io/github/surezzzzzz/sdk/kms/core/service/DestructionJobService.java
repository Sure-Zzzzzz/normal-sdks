package io.github.surezzzzzz.sdk.kms.core.service;

/**
 * 销毁任务服务。
 *
 * <p>实现负责扫描到期及租约过期任务、领取、续租和处理；因此每次处理均可恢复此前实例遗留的过期
 * 租约。实际销毁必须在同一事务内重新确认逻辑密钥、版本和任务都仍处于待销毁状态，并在材料置空、
 * 审计和任务完成提交后才视为成功。</p>
 *
 * @author surezzzzzz
 */
public interface DestructionJobService {

    /**
     * 由指定 worker 实例处理可领取的到期销毁任务。
     */
    void processDueJobs(String instanceId);
}
