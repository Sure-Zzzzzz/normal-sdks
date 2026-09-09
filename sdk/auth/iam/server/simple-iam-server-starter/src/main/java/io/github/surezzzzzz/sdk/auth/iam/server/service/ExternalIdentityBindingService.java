package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 外部身份绑定服务（管理员预绑定）
 *
 * <p>同名并号的唯一合法路径：管理员显式把外部身份绑定到本地账号，或解除绑定。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class ExternalIdentityBindingService {

    private final IamUserRepository userRepository;
    private final ExternalProviderRegistry providerRegistry;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 将外部身份绑定到本地用户。
     *
     * @param userId       本地用户 ID
     * @param providerCode 登录方式编码（须已装配）
     * @param externalId   外部体系稳定 ID
     * @return 绑定后的用户
     */
    @Transactional
    public IamUserEntity bind(Long userId, String providerCode, String externalId) {
        validate(providerCode, externalId);
        if (userRepository.existsByIdentitySourceAndExternalId(providerCode, externalId)) {
            throw new SimpleIamServerException(
                    String.format(ServerErrorMessage.EXTERNAL_IDENTITY_ALREADY_BOUND, externalId));
        }
        IamUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.USER_NOT_FOUND,
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId)));
        user.setIdentitySource(providerCode);
        user.setExternalId(externalId);
        user.setUpdatedAt(java.time.Instant.now());
        IamUserEntity saved = userRepository.save(user);
        log.info("外部身份绑定：userId={}, provider={}, externalId={}", userId, providerCode, externalId);
        auditEventPublisher.publishAdminAction(AdminActionType.BOUND, AdminSubjectType.EXTERNAL_BINDING,
                String.valueOf(userId), user.getUsername(), providerCode + ":" + externalId);
        return saved;
    }

    /**
     * 解除用户的外部身份绑定（账号恢复为本地账号）。
     *
     * <p>预绑定的本地账号解绑后即可恢复本地密码登录；JIT 开号的账号本地密码哈希
     * 为随机值，解绑后无法用本地密码登录，需管理员重置密码后才能恢复。</p>
     *
     * @param userId 本地用户 ID
     */
    @Transactional
    public void unbind(Long userId) {
        IamUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.USER_NOT_FOUND,
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId)));
        if (user.getIdentitySource() != null || user.getExternalId() != null) {
            String previousBinding = user.getIdentitySource() + ":" + user.getExternalId();
            user.setIdentitySource(null);
            user.setExternalId(null);
            user.setUpdatedAt(java.time.Instant.now());
            userRepository.save(user);
            log.info("外部身份解绑：userId={}, username={}", userId, user.getUsername());
            auditEventPublisher.publishAdminAction(AdminActionType.UNBOUND, AdminSubjectType.EXTERNAL_BINDING,
                    String.valueOf(userId), user.getUsername(), previousBinding);
        }
    }

    private void validate(String providerCode, String externalId) {
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(externalId)) {
            throw new SimpleIamServerException(ServerErrorMessage.EXTERNAL_IDENTITY_INVALID);
        }
        if (!providerRegistry.contains(providerCode)) {
            throw new SimpleIamServerException(
                    String.format(ServerErrorMessage.EXTERNAL_IDENTITY_PROVIDER_UNKNOWN, providerCode));
        }
    }
}
