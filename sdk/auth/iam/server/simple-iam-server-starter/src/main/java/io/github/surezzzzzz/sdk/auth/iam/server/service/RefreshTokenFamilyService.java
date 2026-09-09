package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.RefreshTokenReuseDetectedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRefreshTokenFamilyRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Refresh Token 族服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class RefreshTokenFamilyService {

    private final IamRefreshTokenFamilyRepository refreshTokenFamilyRepository;
    private final RedisTokenRepository redisTokenRepository;
    private final SimpleIamServerProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建 Refresh Token 族
     *
     * @param userId       用户 ID
     * @param username     用户名
     * @param sessionId    会话 ID
     * @param refreshToken 当前 refresh token 原文
     * @return Refresh Token 族实体
     */
    @Transactional
    public IamRefreshTokenFamilyEntity createFamily(Long userId, String username,
                                                    String sessionId, String refreshToken) {
        String familyId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getToken().getRefreshExpiresIn());

        IamRefreshTokenFamilyEntity family = new IamRefreshTokenFamilyEntity();
        family.setId(familyId);
        family.setUserId(userId);
        family.setUsername(username);
        family.setSessionId(sessionId);
        family.setCurrentTokenHash(TokenHashHelper.sha256Hex(refreshToken));
        family.setIssuedAt(now);
        family.setExpiresAt(expiresAt);
        family.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);

        IamRefreshTokenFamilyEntity saved = refreshTokenFamilyRepository.save(family);
        redisTokenRepository.saveRefreshFamily(familyId, saved, Duration.ofSeconds(properties.getToken().getRefreshExpiresIn()));
        return saved;
    }

    /**
     * 验证并读取 Refresh Token 族
     *
     * @param refreshToken refresh token 原文
     * @return Refresh Token 族实体
     */
    public IamRefreshTokenFamilyEntity validateCurrentToken(String refreshToken) {
        String tokenHash = TokenHashHelper.sha256Hex(refreshToken);
        IamRefreshTokenFamilyEntity family = refreshTokenFamilyRepository.findByCurrentTokenHash(tokenHash)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "Refresh Token 无效"));
        if (family.getStatus() == null || family.getStatus() != SimpleIamServerConstant.STATUS_ACTIVE) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "Refresh Token 族已撤销");
        }
        if (family.getExpiresAt().isBefore(Instant.now())) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_EXPIRED, "Refresh Token 已过期");
        }
        return family;
    }

    /**
     * 检测 Refresh Token 是否复用，复用则撤销整族
     *
     * @param refreshToken refresh token 原文
     */
    @Transactional(noRollbackFor = SimpleIamServerException.class)
    public void detectReuseAndRevoke(String refreshToken) {
        String tokenHash = TokenHashHelper.sha256Hex(refreshToken);
        refreshTokenFamilyRepository.findByPreviousTokenHash(tokenHash).ifPresent(family -> {
            revokeFamily(family.getId());
            eventPublisher.publishEvent(new RefreshTokenReuseDetectedEvent(this,
                    family.getId(), null, String.valueOf(family.getUserId()), family.getUsername(),
                    family.getIssuedAt(), family.getExpiresAt()));
            throw new SimpleIamServerException(ErrorCode.REFRESH_TOKEN_REUSE, ServerErrorMessage.REFRESH_TOKEN_REUSE);
        });
    }

    /**
     * 轮换 Refresh Token
     *
     * @param familyId        族 ID
     * @param newRefreshToken 新 refresh token 原文
     * @return 轮换后的实体
     */
    @Transactional
    public IamRefreshTokenFamilyEntity rotate(String familyId, String newRefreshToken) {
        IamRefreshTokenFamilyEntity family = refreshTokenFamilyRepository.findById(familyId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "Refresh Token 族不存在：" + familyId));
        family.setPreviousTokenHash(family.getCurrentTokenHash());
        family.setCurrentTokenHash(TokenHashHelper.sha256Hex(newRefreshToken));
        family.setRotatedAt(Instant.now());
        IamRefreshTokenFamilyEntity saved = refreshTokenFamilyRepository.save(family);
        redisTokenRepository.saveRefreshFamily(familyId, saved,
                Duration.between(Instant.now(), saved.getExpiresAt()));
        return saved;
    }

    /**
     * 撤销 Refresh Token 族
     *
     * @param familyId 族 ID
     */
    @Transactional
    public void revokeFamily(String familyId) {
        IamRefreshTokenFamilyEntity family = refreshTokenFamilyRepository.findById(familyId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TOKEN_INVALID, "Refresh Token 族不存在：" + familyId));
        family.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        family.setRevokedAt(Instant.now());
        refreshTokenFamilyRepository.save(family);
        redisTokenRepository.deleteRefreshFamily(familyId);
    }

    /**
     * 撤销用户全部 Refresh Token 族
     *
     * @param userId 用户 ID
     * @return 撤销数量
     */
    @Transactional
    public int revokeAllByUserId(Long userId) {
        return refreshTokenFamilyRepository.revokeAllByUserId(userId, Instant.now());
    }

    /**
     * 清理已过期 Token 族
     *
     * @return 清理数量
     */
    @Transactional
    public int cleanupExpiredFamilies() {
        List<IamRefreshTokenFamilyEntity> expired = refreshTokenFamilyRepository.findByExpiresAtBeforeAndStatus(
                Instant.now(), SimpleIamServerConstant.STATUS_ACTIVE);
        for (IamRefreshTokenFamilyEntity family : expired) {
            family.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
            family.setRevokedAt(Instant.now());
            redisTokenRepository.deleteRefreshFamily(family.getId());
        }
        refreshTokenFamilyRepository.saveAll(expired);
        return expired.size();
    }
}
