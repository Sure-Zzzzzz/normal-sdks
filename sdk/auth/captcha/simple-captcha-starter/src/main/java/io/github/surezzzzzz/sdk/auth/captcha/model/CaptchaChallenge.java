package io.github.surezzzzzz.sdk.auth.captcha.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Captcha Challenge
 *
 * <p>验证码挑战：type=image 时 content 为 data URI（前端 img 标签直接使用）；
 * 其他类型（滑块等）content 为前端组件所需参数，由实现方与前端约定。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class CaptchaChallenge {

    /**
     * 挑战 id（verify 时回传）
     */
    private final String captchaId;

    /**
     * 挑战类型（image 等，见实现方约定）
     */
    private final String type;

    /**
     * 挑战内容（图片 data URI / 前端组件参数）
     */
    private final String content;
}
