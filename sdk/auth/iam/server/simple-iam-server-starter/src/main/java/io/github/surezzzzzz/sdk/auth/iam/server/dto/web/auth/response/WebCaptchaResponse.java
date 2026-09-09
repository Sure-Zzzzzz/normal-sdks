package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Web 人机验证挑战响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebCaptchaResponse {

    /**
     * 挑战唯一标识（登录提交时原样带回 captchaId）
     */
    private String captchaId;

    /**
     * 挑战类型（如 image）
     */
    private String type;

    /**
     * 前端展示内容（type=image 时为 data URI，img 标签直接使用）
     */
    private String content;
}
