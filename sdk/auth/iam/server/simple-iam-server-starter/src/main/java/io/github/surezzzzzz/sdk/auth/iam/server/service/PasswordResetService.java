package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPasswordResetEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamPasswordResetRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 密码重置服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class PasswordResetService {

    private final IamPasswordResetRepository passwordResetRepository;
    private final RedisTokenRepository redisTokenRepository;
    private final UserService userService;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 创建密码重置凭证
     *
     * @param userId      用户 ID
     * @param requestedBy 发起人
     * @return 重置 token 原文，仅返回一次
     */
    @Transactional
    public String createResetToken(Long userId, String requestedBy) {
        IamUserEntity user = userService.getById(userId);
        String token = UUID.randomUUID().toString();
        String tokenHash = TokenHashHelper.sha256Hex(token);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(SimpleIamServerConstant.DEFAULT_PASSWORD_RESET_TOKEN_EXPIRES_MINUTES));

        IamPasswordResetEntity entity = new IamPasswordResetEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setUsername(user.getUsername());
        entity.setTokenHash(tokenHash);
        entity.setRequestedBy(requestedBy);
        entity.setIssuedAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);

        IamPasswordResetEntity saved = passwordResetRepository.save(entity);
        redisTokenRepository.savePasswordReset(token, saved, Duration.ofMinutes(SimpleIamServerConstant.DEFAULT_PASSWORD_RESET_TOKEN_EXPIRES_MINUTES));
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.PASSWORD_RESET_TOKEN,
                saved.getId(), user.getUsername(),
                requestedBy == null ? null : "requestedBy=" + requestedBy);
        return token;
    }

    /**
     * 使用重置凭证修改密码
     *
     * @param token       重置 token 原文
     * @param newPassword 新密码
     */
    @Transactional
    public void resetByToken(String token, String newPassword) {
        IamPasswordResetEntity credential = validateToken(token);

        userService.resetPassword(credential.getUserId(), newPassword, credential.getRequestedBy());

        credential.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        credential.setUsedAt(Instant.now());
        passwordResetRepository.save(credential);
        redisTokenRepository.deletePasswordReset(token);
    }

    /**
     * 校验密码重置凭证
     *
     * @param token 重置 token 原文
     * @return 凭证实体
     */
    public IamPasswordResetEntity validateToken(String token) {
        IamPasswordResetEntity cached = redisTokenRepository.getPasswordReset(token);
        if (cached != null) {
            validateEntity(cached);
            return cached;
        }
        IamPasswordResetEntity entity = passwordResetRepository.findByTokenHash(TokenHashHelper.sha256Hex(token))
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "密码重置凭证无效"));
        validateEntity(entity);
        return entity;
    }

    /**
     * 撤销重置凭证
     *
     * @param token 重置 token 原文
     */
    @Transactional
    public void revokeToken(String token) {
        IamPasswordResetEntity entity = passwordResetRepository.findByTokenHash(TokenHashHelper.sha256Hex(token))
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "密码重置凭证无效"));
        entity.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        passwordResetRepository.save(entity);
        redisTokenRepository.deletePasswordReset(token);
        auditEventPublisher.publishAdminAction(AdminActionType.REVOKED, AdminSubjectType.PASSWORD_RESET_TOKEN,
                entity.getId(), entity.getUsername(), null);
    }

    /**
     * 清理已过期重置凭证
     *
     * @return 清理数量
     */
    @Transactional
    public int cleanupExpiredTokens() {
        List<IamPasswordResetEntity> expired = passwordResetRepository.findByExpiresAtBeforeAndStatus(
                Instant.now(), SimpleIamServerConstant.STATUS_ACTIVE);
        for (IamPasswordResetEntity entity : expired) {
            entity.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        }
        passwordResetRepository.saveAll(expired);
        return expired.size();
    }

    private void validateEntity(IamPasswordResetEntity entity) {
        if (entity.getStatus() == null || entity.getStatus() != SimpleIamServerConstant.STATUS_ACTIVE) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "密码重置凭证已失效");
        }
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_EXPIRED, "密码重置凭证已过期");
        }
    }
}
