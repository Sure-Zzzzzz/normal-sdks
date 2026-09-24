package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import lombok.RequiredArgsConstructor;

/**
 * 可信应用删除任务执行协调器。
 *
 * <p>领取和执行分别通过生命周期服务的独立事务完成，不能在同一个 Bean 内部调用
 * {@code REQUIRES_NEW} 方法，否则 Spring 代理不会生效。</p>
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamTrustedApplicationCleanupWorker {

    private final IamTrustedApplicationLifecycleService lifecycleService;

    /**
     * 尝试领取并处理一个操作；没有可处理记录时无操作。
     */
    public void processNextOperation() {
        IamTrustedApplicationCleanupLease lease = lifecycleService.claimNextOperation();
        if (lease != null) {
            lifecycleService.processClaimedOperation(lease);
        }
    }
}
