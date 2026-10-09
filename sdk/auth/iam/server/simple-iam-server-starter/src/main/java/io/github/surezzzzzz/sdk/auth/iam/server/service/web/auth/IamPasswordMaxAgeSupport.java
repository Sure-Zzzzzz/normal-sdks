package io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.MustChangePasswordReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

/**
 * 口令最长生存期策略（1.3.6）：策略关闭（maxAgeDays=0）时零行为；开启后仅本地账号在
 * 登录密码验证通过时判定——过期置须改密标记（复用既有受限会话通道），临期返回剩余天数
 * 供登录响应与 /me 携带。存量行 password_updated_at 为 null 时首登回填为登录时刻
 * （激活基线），避免一刀切把全量存量用户立即判过期。设计见 DESIGN.1.3.6。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPasswordMaxAgeSupport {

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    private final SimpleIamServerProperties properties;

    /**
     * 登录判定（密码验证通过后调用，调用方事务内持久化）。
     *
     * <p>边界：elapsed &gt;= maxAge 即过期（第 maxAge 天当天登录=过期置位，不存在"剩 0 天"态）；
     * 时间戳在未来（时钟回拨/手工改库）按剩余=满额处理，不产生负数。</p>
     *
     * @param user 已通过密码验证的本地账号实体
     * @return 临期剩余天数（1..warnBeforeDays）；无需携带（不临期/已过期/策略关闭）返回 empty
     */
    public Optional<Integer> applyOnLocalLogin(IamUserEntity user) {
        int maxAgeDays = properties.getPassword().getMaxAgeDays();
        if (maxAgeDays <= 0 || user.getPasswordUpdatedAt() == null) {
            if (maxAgeDays > 0 && user.getPasswordUpdatedAt() == null) {
                // 策略开启后首次登录：回填激活基线，本轮按满额不判定过期
                user.setPasswordUpdatedAt(Instant.now());
                log.debug("口令生存期激活基线回填：username={}, maxAgeDays={}", user.getUsername(), maxAgeDays);
            }
            return Optional.empty();
        }
        long elapsedDays = elapsedDays(user.getPasswordUpdatedAt());
        if (elapsedDays >= maxAgeDays) {
            user.setMustChangePassword(Boolean.TRUE);
            user.setMustChangePasswordReason(MustChangePasswordReason.PASSWORD_EXPIRED);
            log.debug("口令生存期过期置位：username={}, elapsedDays={}, maxAgeDays={}",
                    user.getUsername(), elapsedDays, maxAgeDays);
            return Optional.empty();
        }
        int remainingDays = (int) (maxAgeDays - elapsedDays);
        return remainingDays <= properties.getPassword().getWarnBeforeDays()
                ? Optional.of(remainingDays) : Optional.empty();
    }

    /**
     * 剩余天数只读计算（/me 与管理面详情用，不改实体）。与登录判定同口径：仅临期窗口内
     * （1..warnBeforeDays）或已过期（0）携带，满额不携带（零打扰，契约同款语义）。
     *
     * @return 策略关闭/未激活/满额返回 empty；已过期返回 0
     */
    public Optional<Integer> daysRemaining(IamUserEntity user) {
        int maxAgeDays = properties.getPassword().getMaxAgeDays();
        if (maxAgeDays <= 0 || user.getPasswordUpdatedAt() == null) {
            return Optional.empty();
        }
        long elapsedDays = elapsedDays(user.getPasswordUpdatedAt());
        if (elapsedDays >= maxAgeDays) {
            return Optional.of(0);
        }
        int remainingDays = (int) (maxAgeDays - elapsedDays);
        return remainingDays <= properties.getPassword().getWarnBeforeDays()
                ? Optional.of(remainingDays) : Optional.empty();
    }

    private long elapsedDays(Instant passwordUpdatedAt) {
        long elapsedMillis = System.currentTimeMillis() - passwordUpdatedAt.toEpochMilli();
        if (elapsedMillis <= 0) {
            // 时间戳在未来：防御性按刚设置处理（剩余=满额）
            return 0;
        }
        return elapsedMillis / MILLIS_PER_DAY;
    }

    /**
     * 配置合法性：提醒窗口不得大于生存期（仅生存期开启时校验）。
     *
     * @return 非法时返回错误描述，合法返回 null
     */
    public String validateConfiguration() {
        int maxAgeDays = properties.getPassword().getMaxAgeDays();
        if (maxAgeDays > 0 && properties.getPassword().getWarnBeforeDays() > maxAgeDays) {
            return String.format(ServerErrorMessage.PASSWORD_WARN_WINDOW_EXCEEDS_MAX_AGE,
                    properties.getPassword().getWarnBeforeDays(), maxAgeDays);
        }
        return null;
    }
}
