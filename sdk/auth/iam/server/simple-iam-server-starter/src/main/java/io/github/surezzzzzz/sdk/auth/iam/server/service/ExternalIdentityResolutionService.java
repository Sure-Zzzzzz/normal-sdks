package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 外部身份归一服务
 *
 * <p>将认证成功的外部身份映射为本地用户，三步策略：
 * 已绑定直接复用；同名本地账号绝不自动并号（防接管）；其余按开号模式 JIT 开号或拒绝。
 * JIT 开号的密码哈希为随机 UUID 编码值，外部账号永远无法用本地密码登录。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class ExternalIdentityResolutionService {

    private final IamUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpleIamServerProperties properties;
    private final LoginFailurePolicySupport failurePolicySupport;

    /**
     * 归一外部身份为可登录的本地用户（含禁用/锁定检查）。
     *
     * @param identity 认证成功的外部身份
     * @return 可登录的本地用户
     */
    @Transactional
    public IamUserEntity resolve(ExternalIdentity identity) {
        Optional<IamUserEntity> bound = userRepository.findByIdentitySourceAndExternalId(
                identity.getProviderCode(), identity.getExternalId());
        if (bound.isPresent()) {
            IamUserEntity user = bound.get();
            failurePolicySupport.assertAccountUsable(user);
            return user;
        }
        return provision(identity);
    }

    private IamUserEntity provision(ExternalIdentity identity) {
        String suggestedUsername = identity.getUsernameSuggestion();
        if (suggestedUsername == null || suggestedUsername.trim().isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.EXTERNAL_IDENTITY_USERNAME_INVALID,
                    ServerErrorMessage.EXTERNAL_IDENTITY_USERNAME_INVALID);
        }
        suggestedUsername = suggestedUsername.trim();

        // 同名本地账号存在：绝不自动并号，需管理员显式预绑定
        if (userRepository.existsByUsername(suggestedUsername)) {
            throw new SimpleIamServerException(ErrorCode.EXTERNAL_IDENTITY_NOT_BOUND,
                    ServerErrorMessage.EXTERNAL_IDENTITY_NOT_BOUND);
        }

        if (SimpleIamServerConstant.PROVISIONING_MODE_PRE_BOUND_ONLY
                .equals(properties.getExternalIdentity().getProvisioningMode())) {
            throw new SimpleIamServerException(ErrorCode.EXTERNAL_IDENTITY_NOT_BOUND,
                    ServerErrorMessage.EXTERNAL_IDENTITY_NOT_BOUND);
        }

        IamUserEntity user = new IamUserEntity();
        user.setUsername(suggestedUsername);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setDisplayName(defaultString(identity.getDisplayName(), suggestedUsername));
        user.setEmail(identity.getEmail());
        user.setIdentitySource(identity.getProviderCode());
        user.setExternalId(identity.getExternalId());
        user.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        IamUserEntity saved = userRepository.save(user);
        log.info("外部身份首次登录自动开号：provider={}, username={}, userId={}",
                identity.getProviderCode(), suggestedUsername, saved.getId());
        return saved;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
