package io.github.surezzzzzz.sdk.auth.iam.core.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人机验证挑战。
 *
 * <p>由提供方在 {@link CaptchaProvider#generate} 成功后构造；
 * captchaId 为挑战唯一标识（请求方提交答案时原样带回），
 * content 为前端可直接渲染的展示内容（type=image 时为 data URI）。</p>
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class CaptchaChallenge {

    /**
     * 挑战唯一标识（一次性，verify 时提交）。
     */
    private final String captchaId;

    /**
     * 挑战类型（如 image）。
     */
    private final String type;

    /**
     * 前端展示内容（type=image 时为 PNG data URI，img 标签直接使用）。
     */
    private final String content;
}
