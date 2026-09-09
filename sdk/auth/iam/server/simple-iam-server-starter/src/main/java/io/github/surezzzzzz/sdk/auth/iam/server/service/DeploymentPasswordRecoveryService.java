package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPasswordResetEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamPasswordResetRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 部署侧一次性管理员密码恢复服务。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class DeploymentPasswordRecoveryService {

    private final SimpleIamServerProperties properties;
    private final IamPasswordResetRepository passwordResetRepository;
    private final IamUserRepository userRepository;
    private final UserService userService;
    private final SessionService sessionService;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 在启动引导阶段消费部署注入的恢复码；恢复码永不进入 HTTP 或浏览器边界。
     *
     * @param bootstrapInitialPassword 本次首次引导生成的密码；非空时无需再次重置
     */
    @Transactional
    public void recoverBootstrapAdministratorIfRequested(String bootstrapInitialPassword) {
        String recoveryCode = properties.getBootstrap().getRecoveryCode();
        if (!StringUtils.hasText(recoveryCode)) {
            return;
        }

        IamUserEntity user = userRepository.findByUsername(properties.getBootstrap().getUsername())
                .orElseThrow(() -> new ConfigurationException("启动引导管理员不存在："
                        + properties.getBootstrap().getUsername()));
        String recoveryCodeHash = TokenHashHelper.sha256Hex(recoveryCode);
        IamPasswordResetEntity credential = passwordResetRepository
                .findByIdAndRequestedBy(SimpleIamServerConstant.DEPLOYMENT_RECOVERY_CREDENTIAL_ID,
                        SimpleIamServerConstant.DEPLOYMENT_RECOVERY_REQUESTED_BY)
                .orElse(null);
        if (credential != null && MessageDigest.isEqual(recoveryCodeHash.getBytes(StandardCharsets.UTF_8),
                credential.getTokenHash().getBytes(StandardCharsets.UTF_8))) {
            return;
        }

        Instant now = Instant.now();
        if (credential == null) {
            credential = new IamPasswordResetEntity();
            credential.setId(SimpleIamServerConstant.DEPLOYMENT_RECOVERY_CREDENTIAL_ID);
            credential.setRequestedBy(SimpleIamServerConstant.DEPLOYMENT_RECOVERY_REQUESTED_BY);
        }
        credential.setUserId(user.getId());
        credential.setUsername(user.getUsername());
        credential.setTokenHash(recoveryCodeHash);
        credential.setIssuedAt(now);
        credential.setExpiresAt(now);
        credential.setUsedAt(now);
        credential.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);

        if (StringUtils.hasText(bootstrapInitialPassword)) {
            passwordResetRepository.save(credential);
            log.info("管理员首次引导已完成，部署恢复码已消费：username={}", user.getUsername());
            return;
        }

        String recoveryPassword = properties.getBootstrap().getRecoveryPassword();
        if (!StringUtils.hasText(recoveryPassword)) {
            throw new ConfigurationException("部署恢复密码未配置");
        }
        userService.resetPassword(user.getId(), recoveryPassword,
                SimpleIamServerConstant.DEPLOYMENT_RECOVERY_REQUESTED_BY);
        sessionService.revokeAllByUserId(user.getId());
        passwordResetRepository.save(credential);
        log.info("部署恢复完成：username={}", user.getUsername());
        auditEventPublisher.publishAdminAction(AdminActionType.RECOVERED, AdminSubjectType.BOOTSTRAP_ADMIN,
                String.valueOf(user.getId()), user.getUsername(), null);
    }
}
