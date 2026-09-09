package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AuthenticationEventType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 登录失败策略支撑
 *
 * <p>统一承载本地密码登录与外部身份源登录共用的禁用/锁定检查、失败计数与成功回写；
 * 本地计数按 username 维度，外部计数按 providerCode + username 维度，互不污染。
 * 失败计数达到阈值触发锁定时发布账号锁定审计事件。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class LoginFailurePolicySupport {

    private final IamUserRepository userRepository;
    private final RedisTokenRepository redisTokenRepository;
    private final SimpleIamServerProperties properties;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 断言账号可登录：未禁用、未锁定、本地密码维度失败计数未超限。
     *
     * @param user 目标用户
     */
    public void assertLocalLoginAllowed(IamUserEntity user) {
        assertAccountUsable(user);
        assertFailureCountWithinLimit(redisTokenRepository.getLoginFailureCount(user.getUsername()));
    }

    /**
     * 记录一次本地密码登录失败并按阈值锁定。
     *
     * <p>锁定写库走独立事务（REQUIRES_NEW）：调用方登录事务随后抛凭据异常回滚，
     * 锁定状态必须先落库生效，否则管理端永远看不到锁定、手动解锁无处可点。</p>
     *
     * @param user 登录失败的目标用户
     * @return 记录后的累计失败次数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long recordLocalFailure(IamUserEntity user) {
        Duration lockWindow = lockWindow();
        String username = user.getUsername();
        Long count = redisTokenRepository.incrementLoginFailure(username, lockWindow);
        log.warn("登录失败：username={}, 累计失败次数={}", username, count);
        lockOnExceeded(user, count);
        return count == null ? 0 : count;
    }

    /**
     * 记录用户名不存在场景的本地密码登录失败（仅递增计数，无账号可锁定）。
     *
     * <p>计数口径与真实用户一致：爆破不存在的用户名同样渐进触发人机验证，
     * 不得因用户不存在而豁免。</p>
     *
     * @param username 登录尝试的用户名
     * @return 记录后的累计失败次数
     */
    public long recordLocalFailureForUnknownUsername(String username) {
        Long count = redisTokenRepository.incrementLoginFailure(username, lockWindow());
        log.warn("登录失败（用户不存在）：username={}, 累计失败次数={}", username, count);
        return count == null ? 0 : count;
    }

    /**
     * 本地密码登录成功：清零失败计数并回写登录状态。
     *
     * @param user 认证通过的用户
     */
    @Transactional
    public void recordLocalSuccess(IamUserEntity user) {
        redisTokenRepository.deleteLoginFailure(user.getUsername());
        markLoginSuccess(user);
    }

    /**
     * 断言外部登录尝试未被失败计数阻断（在调用外部身份源前执行，防爆破）。
     *
     * @param providerCode 登录方式编码
     * @param username     用户名
     */
    public void assertExternalAttemptAllowed(String providerCode, String username) {
        assertFailureCountWithinLimit(
                redisTokenRepository.getExternalLoginFailureCount(providerCode, username));
    }

    /**
     * 记录一次外部登录失败（凭据错误）。
     *
     * @param providerCode 登录方式编码
     * @param username     用户名
     * @return 记录后的累计失败次数
     */
    public long recordExternalFailure(String providerCode, String username) {
        Long count = redisTokenRepository.incrementExternalLoginFailure(providerCode, username, lockWindow());
        log.warn("外部登录失败：provider={}, username={}, 累计失败次数={}", providerCode, username, count);
        return count == null ? 0 : count;
    }

    /**
     * 按锁定上限计算剩余可尝试次数（达上限返回 0）。
     *
     * @param failureCount 当前累计失败次数
     * @return 距离锁定的剩余次数
     */
    public int remainingAttempts(long failureCount) {
        int maxAttempts = properties.getLogin().getMaxAttempts();
        return failureCount >= maxAttempts ? 0 : (int) (maxAttempts - failureCount);
    }

    /**
     * 外部登录成功：清零该维度失败计数并回写登录状态。
     *
     * @param providerCode 登录方式编码
     * @param username     用户名
     * @param user         认证通过的用户
     */
    @Transactional
    public void recordExternalSuccess(String providerCode, String username, IamUserEntity user) {
        redisTokenRepository.deleteExternalLoginFailure(providerCode, username);
        markLoginSuccess(user);
    }

    /**
     * 断言账号未禁用且未处于 DB 锁定期。
     *
     * @param user 目标用户
     */
    public void assertAccountUsable(IamUserEntity user) {
        if (user.getStatus() != null && user.getStatus() == SimpleIamServerConstant.STATUS_INACTIVE) {
            throw new SimpleIamServerException(ErrorCode.USER_DISABLED,
                    String.format(ServerErrorMessage.USER_DISABLED, user.getUsername()));
        }
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            throw new SimpleIamServerException(
                    ErrorCode.ACCOUNT_LOCKED, ServerErrorMessage.ACCOUNT_LOCKED);
        }
    }

    private void assertFailureCountWithinLimit(int failCount) {
        if (failCount >= properties.getLogin().getMaxAttempts()) {
            throw new SimpleIamServerException(
                    ErrorCode.ACCOUNT_LOCKED, ServerErrorMessage.ACCOUNT_LOCKED);
        }
    }

    private void lockOnExceeded(IamUserEntity user, Long count) {
        if (count >= properties.getLogin().getMaxAttempts()) {
            user.setLockedUntil(Instant.now().plus(lockWindow()));
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
            log.warn("账号已锁定：username={}, 锁定到={}", user.getUsername(), user.getLockedUntil());
            auditEventPublisher.publishAuthentication(AuthenticationEventType.ACCOUNT_LOCKED,
                    SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD,
                    user.getUsername(), user.getId(), null, null, null,
                    count == null ? null : count.intValue());
        }
    }

    private void markLoginSuccess(IamUserEntity user) {
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    private Duration lockWindow() {
        return Duration.ofMinutes(properties.getLogin().getLockMinutes());
    }
}
