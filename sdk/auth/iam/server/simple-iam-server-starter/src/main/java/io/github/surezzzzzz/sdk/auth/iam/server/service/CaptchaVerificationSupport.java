package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaProvider;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * 登录链路人机验证支撑（渐进验证码策略）
 *
 * <p>失败计数达到阈值后要求请求方完成人机验证：本地密码按 username 维度、
 * 外部凭证源按 providerCode + username 维度判定（与失败计数分维度一致），
 * 跳转型登录不经本支撑（认证在外部 IdP，IAM 侧 state 已防重放）。</p>
 *
 * <p>时序语义：验证码校验先于凭据校验执行，验证码失败不递增密码失败计数
 * （凭据校验未发生）；达到锁定上限的拦截仍由既有登录失败策略兜底，
 * 本支撑只看阈值下限。captcha.enabled 开启但宿主未装配任何
 * {@link CaptchaProvider} 时降级放行并告警（验证码是防爆破辅助，
 * 主防线失败计数与锁定不受影响，不得因组件缺失阻断登录）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class CaptchaVerificationSupport {

    private final ObjectProvider<CaptchaProvider> captchaProviderProvider;
    private final RedisTokenRepository redisTokenRepository;
    private final SimpleIamServerProperties properties;

    /**
     * 登录前置判定：达到验证码阈值时校验挑战答案，未达阈值或开关关闭时直接放行。
     *
     * @param providerCode  登录方式编码（本地密码或凭证型外部源）
     * @param username      用户名
     * @param captchaId     请求携带的挑战 id（可为 null）
     * @param captchaAnswer 请求携带的答案（可为 null）
     */
    public void enforce(String providerCode, String username, String captchaId, String captchaAnswer) {
        if (!Boolean.TRUE.equals(properties.getCaptcha().getEnabled())) {
            return;
        }
        CaptchaProvider provider = captchaProviderProvider.getIfAvailable();
        if (provider == null) {
            log.warn("人机验证已开启但未装配 CaptchaProvider，本次降级放行：username={}", username);
            return;
        }
        if (failureCount(providerCode, username) < properties.getCaptcha().getThreshold()) {
            return;
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaAnswer)) {
            log.info("登录要求人机验证：provider={}, username={}", providerCode, username);
            throw new SimpleIamServerException(ErrorCode.CAPTCHA_REQUIRED, ServerErrorMessage.CAPTCHA_REQUIRED);
        }
        if (!provider.verify(captchaId.trim(), captchaAnswer)) {
            log.info("人机验证未通过：provider={}, username={}", providerCode, username);
            throw new SimpleIamServerException(ErrorCode.CAPTCHA_INVALID, ServerErrorMessage.CAPTCHA_INVALID);
        }
    }

    /**
     * 当前登录维度的失败计数（与失败计数策略同维度：本地按 username、外部按 provider+username）
     */
    private int failureCount(String providerCode, String username) {
        return SimpleIamServerConstant.LOGIN_PROVIDER_LOCAL_PASSWORD.equals(providerCode)
                ? redisTokenRepository.getLoginFailureCount(username)
                : redisTokenRepository.getExternalLoginFailureCount(providerCode, username);
    }
}
