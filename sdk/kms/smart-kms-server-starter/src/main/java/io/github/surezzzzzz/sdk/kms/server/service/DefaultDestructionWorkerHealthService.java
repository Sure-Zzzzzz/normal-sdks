package io.github.surezzzzzz.sdk.kms.server.service;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerHealth;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerState;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionJobRepository;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionWorkerStateRepository;
import io.github.surezzzzzz.sdk.kms.core.service.DestructionWorkerHealthService;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerProperties;

import java.time.Instant;
import java.util.Optional;

/**
 * 默认销毁 worker 健康事实服务。
 *
 * @author surezzzzzz
 */
public class DefaultDestructionWorkerHealthService implements DestructionWorkerHealthService {

    private final KmsClock clock;
    private final KmsDestructionJobRepository destructionJobRepository;
    private final KmsDestructionWorkerStateRepository workerStateRepository;
    private final SmartKmsServerProperties properties;

    /**
     * 创建默认 worker 健康服务。
     */
    public DefaultDestructionWorkerHealthService(KmsClock clock, KmsDestructionJobRepository destructionJobRepository,
                                                 KmsDestructionWorkerStateRepository workerStateRepository,
                                                 SmartKmsServerProperties properties) {
        this.clock = clock;
        this.destructionJobRepository = destructionJobRepository;
        this.workerStateRepository = workerStateRepository;
        this.properties = properties;
    }

    /**
     * 获取当前实例的可领取状态和延迟事实。
     */
    @Override
    public KmsDestructionWorkerHealth health(String instanceId) {
        int maximumFailures = maximumFailures();
        Optional<KmsDestructionWorkerState> state = workerStateRepository.findByInstanceId(instanceId);
        Instant now = clock.now();
        int failureCount = state.isPresent() ? state.get().getConsecutiveFailureCount() : 0;
        return KmsDestructionWorkerHealth.builder()
                .claimable(failureCount < maximumFailures)
                .lastSuccessfulScanAt(state.isPresent() ? state.get().getLastSuccessfulScanAt() : null)
                .consecutiveFailureCount(failureCount)
                .oldestOverdueDelay(destructionJobRepository.findOldestOverdueDelay(now).orElse(null)).build();
    }

    /**
     * 获取配置确认的连续失败阈值。
     */
    private int maximumFailures() {
        if (properties.getWorker() == null || properties.getWorker().getMaxConsecutiveFailures() == null
                || properties.getWorker().getMaxConsecutiveFailures().intValue() < 1) {
            throw new KmsValidationException();
        }
        return properties.getWorker().getMaxConsecutiveFailures().intValue();
    }
}
