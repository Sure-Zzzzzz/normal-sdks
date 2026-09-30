package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手机号挑战创建响应（未知号统一受理，防枚举）。
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebPhoneChallengeResponse {

    /**
     * 挑战 ID
     */
    private String challengeId;

    /**
     * 允许再次发送的等待秒数（服务端同号冷却配置，前端倒计时唯一事实源）
     */
    private Integer resendAfterSeconds;
}
