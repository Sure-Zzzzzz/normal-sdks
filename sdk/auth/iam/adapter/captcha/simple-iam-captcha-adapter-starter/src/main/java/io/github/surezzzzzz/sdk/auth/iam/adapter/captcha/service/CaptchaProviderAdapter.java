package io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.service;

import io.github.surezzzzzz.sdk.auth.captcha.spi.CaptchaProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.annotation.SimpleIamCaptchaAdapterComponent;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaChallenge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Captcha Provider Adapter
 *
 * <p>通用 captcha 模块 SPI 到 iam-core 登录链路契约的转发委托：
 * 出题 / 验题 / 挑战存储全部由通用模块实现（默认图片码），
 * 本适配器只做契约桥接与模型转换（两模型同构：captchaId / type / content）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamCaptchaAdapterComponent
@RequiredArgsConstructor
public class CaptchaProviderAdapter implements io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaProvider {

    private final CaptchaProvider delegate;

    /**
     * 生成验证码挑战（桥接通用 captcha 组件）
     */
    @Override
    public CaptchaChallenge generate() {
        io.github.surezzzzzz.sdk.auth.captcha.model.CaptchaChallenge challenge = delegate.generate();
        log.debug("验证码挑战已桥接：captchaId={}, type={}", challenge.getCaptchaId(), challenge.getType());
        return new CaptchaChallenge(challenge.getCaptchaId(), challenge.getType(), challenge.getContent());
    }

    /**
     * 校验用户作答
     */
    @Override
    public boolean verify(String captchaId, String answer) {
        return delegate.verify(captchaId, answer);
    }
}
