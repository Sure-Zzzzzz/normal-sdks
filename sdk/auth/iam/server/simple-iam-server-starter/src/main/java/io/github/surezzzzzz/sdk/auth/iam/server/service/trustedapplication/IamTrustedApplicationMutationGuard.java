package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.TrustedApplicationCleanupOperationState;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationCleanupOperationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 可信应用删除期间的统一写入门禁。
 *
 * <p>该类只依赖删除操作表，供生命周期、Portal 和授权配置写侧共同复用，避免这些服务
 * 互相依赖形成 Bean 循环。读取接口不经过本门禁，管理端仍可观察停用和失败状态。</p>
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamTrustedApplicationMutationGuard {

    private static final List<TrustedApplicationCleanupOperationState> BLOCKING_STATES = Arrays.asList(
            TrustedApplicationCleanupOperationState.PENDING,
            TrustedApplicationCleanupOperationState.RUNNING,
            TrustedApplicationCleanupOperationState.RETRYING,
            TrustedApplicationCleanupOperationState.FAILED);

    private final IamTrustedApplicationCleanupOperationRepository cleanupOperationRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;

    /**
     * 删除受理后禁止继续修改同一应用的配置，确保 worker 面对的 client 快照不会移动。
     *
     * @param applicationId 可信应用ID
     */
    public void requireMutable(Long applicationId) {
        if (!trustedApplicationRepository.findByIdForUpdate(applicationId).isPresent()) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId));
        }
        if (cleanupOperationRepository.findFirstByApplicationIdAndStateInOrderByIdDesc(
                applicationId, BLOCKING_STATES).isPresent()) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_MUTATION_BLOCKED,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_MUTATION_BLOCKED, applicationId));
        }
    }
}
