package io.github.surezzzzzz.sdk.auth.captcha.configuration;

import io.github.surezzzzzz.sdk.auth.captcha.constant.SimpleCaptchaConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Captcha Properties
 *
 * <p>被动 SPI 模块：本模块只负责出题 / 验题 / 挑战存储，
 * "何时要求验证码"等业务判定由消费方（如 IAM 登录策略）决定。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleCaptchaConstant.CONFIG_PREFIX)
public class SimpleCaptchaProperties {

    /**
     * 挑战有效期（秒，默认实现消费）
     */
    private long challengeTtlSeconds = SimpleCaptchaConstant.DEFAULT_CHALLENGE_TTL_SECONDS;

    /**
     * 图片码字符个数（默认实现消费）
     */
    private int imageCharLength = SimpleCaptchaConstant.DEFAULT_IMAGE_CHAR_LENGTH;

    /**
     * Redis 配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Redis Config
     */
    @Data
    public static class RedisConfig {

        /**
         * 应用实例标识，用于区分多个应用实例共用 Redis 的场景
         */
        private String me = SimpleCaptchaConstant.DEFAULT_ME;
    }
}
