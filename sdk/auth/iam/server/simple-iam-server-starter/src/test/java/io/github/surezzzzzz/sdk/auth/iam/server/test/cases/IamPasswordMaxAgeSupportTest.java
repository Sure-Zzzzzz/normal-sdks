package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.MustChangePasswordReason;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamPasswordMaxAgeSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 口令最长生存期策略单元测试（纯逻辑，不起 Spring 上下文）。
 *
 * @author surezzzzzz
 */
class IamPasswordMaxAgeSupportTest {

    private IamPasswordMaxAgeSupport support(int maxAgeDays, int warnBeforeDays) {
        SimpleIamServerProperties properties = new SimpleIamServerProperties();
        properties.getPassword().setMaxAgeDays(maxAgeDays);
        properties.getPassword().setWarnBeforeDays(warnBeforeDays);
        return new IamPasswordMaxAgeSupport(properties);
    }

    private IamUserEntity userWithUpdatedAt(Instant passwordUpdatedAt) {
        IamUserEntity user = new IamUserEntity();
        user.setUsername("max-age-user");
        user.setPasswordUpdatedAt(passwordUpdatedAt);
        return user;
    }

    @Test
    @DisplayName("策略关闭（0）：零行为——不回填不置位不携带")
    void disabledPolicyChangesNothing() {
        IamUserEntity user = userWithUpdatedAt(null);
        assertEquals(Optional.empty(), support(0, 7).applyOnLocalLogin(user));
        assertNull(user.getPasswordUpdatedAt(), "关闭时未激活行不得回填");
        assertFalse(Boolean.TRUE.equals(user.getMustChangePassword()));
        assertEquals(Optional.empty(), support(0, 7).daysRemaining(userWithUpdatedAt(Instant.now())));
        assertNull(support(0, 7).validateConfiguration());
    }

    @Test
    @DisplayName("未激活行首登回填激活基线：本轮按满额不判过期")
    void nullTimestampBackfillsAsActivationBaseline() {
        IamUserEntity user = userWithUpdatedAt(null);
        assertEquals(Optional.empty(), support(90, 7).daysRemaining(user), "回填前未激活行剩余天数为空");
        assertEquals(Optional.empty(), support(90, 7).applyOnLocalLogin(user));
        assertTrue(user.getPasswordUpdatedAt() != null
                        && user.getPasswordUpdatedAt().isAfter(Instant.now().minusSeconds(60)),
                "首登应回填为当前时刻");
        assertFalse(Boolean.TRUE.equals(user.getMustChangePassword()), "激活基线轮不得判过期");
    }

    @Test
    @DisplayName("elapsed>=maxAge 即过期：置位并携带 PASSWORD_EXPIRED")
    void expiredMarksMustChangeWithReason() {
        IamUserEntity user = userWithUpdatedAt(Instant.now().minusSeconds(91L * 24 * 60 * 60));
        assertEquals(Optional.empty(), support(90, 7).applyOnLocalLogin(user));
        assertEquals(Boolean.TRUE, user.getMustChangePassword());
        assertEquals(MustChangePasswordReason.PASSWORD_EXPIRED, user.getMustChangePasswordReason());
        assertEquals(Optional.of(0), support(90, 7).daysRemaining(user));
    }

    @Test
    @DisplayName("边界：第 90 天当天登录即过期，不存在剩 0 天态；临期最小剩 1 天")
    void boundaryDayIsExpiredNotZeroRemaining() {
        IamUserEntity exactlyMaxAge = userWithUpdatedAt(Instant.now().minusSeconds(90L * 24 * 60 * 60));
        assertEquals(Optional.empty(), support(90, 7).applyOnLocalLogin(exactlyMaxAge));
        assertEquals(Boolean.TRUE, exactlyMaxAge.getMustChangePassword(), "恰好第 maxAge 天当天=过期");

        IamUserEntity day89 = userWithUpdatedAt(Instant.now().minusSeconds(89L * 24 * 60 * 60));
        assertEquals(Optional.of(1), support(90, 7).applyOnLocalLogin(day89), "临期最小值剩 1 天");
    }

    @Test
    @DisplayName("临期窗口内携带剩余天数，窗口外不携带")
    void warnWindowCarriesRemainingDays() {
        IamUserEntity day85 = userWithUpdatedAt(Instant.now().minusSeconds(85L * 24 * 60 * 60));
        assertEquals(Optional.of(5), support(90, 7).applyOnLocalLogin(day85));

        IamUserEntity day10 = userWithUpdatedAt(Instant.now().minusSeconds(10L * 24 * 60 * 60));
        assertEquals(Optional.empty(), support(90, 7).applyOnLocalLogin(day10), "窗口外零打扰");
    }

    @Test
    @DisplayName("时间戳在未来（时钟回拨/手工改库）：按刚设置处理，剩余=满额")
    void futureTimestampClampedToFull() {
        IamUserEntity future = userWithUpdatedAt(Instant.now().plusSeconds(30L * 24 * 60 * 60));
        assertEquals(Optional.empty(), support(90, 7).applyOnLocalLogin(future));
        assertFalse(Boolean.TRUE.equals(future.getMustChangePassword()));
        assertEquals(Optional.empty(), support(90, 7).daysRemaining(future), "满额（非临期）不携带");
    }

    @Test
    @DisplayName("配置校验：提醒窗口大于生存期（仅开启时）拒绝，关闭时忽略")
    void validateConfigurationRejectsWarnAboveMax() {
        assertEquals("iam.server.password.warn-before-days（30）不得大于 max-age-days（7）",
                support(7, 30).validateConfiguration());
        assertNull(support(7, 7).validateConfiguration(), "等于不大于，合法");
        assertNull(support(0, 30).validateConfiguration(), "关闭时 warn 无意义不校验");
    }
}
