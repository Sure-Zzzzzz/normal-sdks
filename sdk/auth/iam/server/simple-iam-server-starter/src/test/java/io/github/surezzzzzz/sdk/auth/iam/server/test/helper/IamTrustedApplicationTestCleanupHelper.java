package io.github.surezzzzzz.sdk.auth.iam.server.test.helper;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.TrustedApplicationCleanupOperationState;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationCleanupOperationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationCleanupWorker;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IAM 集成测试的可信应用夹具清理器。
 *
 * <p>可信应用删除在生产中异步执行；测试夹具若仅受理删除，会把停用应用和待处理操作遗留给
 * 后续用例，进而使 Portal 的完整快照写门禁受到无关影响。此组件只存在于测试 classpath，
 * 每次清理都推进本地 worker，直至本次操作完成或显式失败。</p>
 *
 * @author surezzzzzz
 */
@Component
@RequiredArgsConstructor
public class IamTrustedApplicationTestCleanupHelper {

    private static final int MAX_WORKER_ATTEMPTS = 100;

    private final IamTrustedApplicationService trustedApplicationService;
    private final IamTrustedApplicationCleanupWorker cleanupWorker;
    private final IamTrustedApplicationCleanupOperationRepository cleanupOperationRepository;

    /**
     * 删除夹具并等待对应异步操作收敛。
     *
     * @param applicationId 待删除可信应用ID；为空时无需处理
     */
    public void deleteAndAwaitCompletion(Long applicationId) {
        if (applicationId == null) {
            return;
        }
        Long operationId = trustedApplicationService.deleteApplication(applicationId).getOperationId();
        for (int attempt = 0; attempt < MAX_WORKER_ATTEMPTS; attempt++) {
            TrustedApplicationCleanupOperationState state = cleanupOperationRepository.findById(operationId)
                    .map(operation -> operation.getState()).orElse(null);
            if (TrustedApplicationCleanupOperationState.COMPLETED == state) {
                return;
            }
            if (TrustedApplicationCleanupOperationState.FAILED == state) {
                throw new AssertionError("可信应用删除任务进入 FAILED：applicationId=" + applicationId
                        + ", operationId=" + operationId);
            }
            cleanupWorker.processNextOperation();
        }
        throw new AssertionError("可信应用删除任务未在测试 worker 推进后完成：applicationId=" + applicationId
                + ", operationId=" + operationId);
    }
}
