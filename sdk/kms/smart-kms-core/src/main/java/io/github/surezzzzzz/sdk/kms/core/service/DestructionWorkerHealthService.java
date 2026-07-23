package io.github.surezzzzzz.sdk.kms.core.service;

import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerHealth;

/**
 * 销毁 worker 健康服务。
 *
 * <p>返回 worker 是否仍可领取任务、最近成功扫描事实及逾期延迟；服务端可据此暴露运行健康状态，
 * 但不得把任务材料或失败异常链暴露到健康响应。</p>
 *
 * @author surezzzzzz
 */
public interface DestructionWorkerHealthService {

    /**
     * 获取指定 worker 实例的销毁任务处理健康事实。
     *
     * @param instanceId worker 实例标识
     * @return 不包含任务材料和异常链的健康事实
     */
    KmsDestructionWorkerHealth health(String instanceId);
}
