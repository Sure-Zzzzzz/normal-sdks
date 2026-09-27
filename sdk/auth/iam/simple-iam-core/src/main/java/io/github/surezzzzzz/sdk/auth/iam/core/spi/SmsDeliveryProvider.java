package io.github.surezzzzzz.sdk.auth.iam.core.spi;

import java.util.List;

/**
 * 短信验证码投递 SPI：能力由 adaptor 装配带来（装配自动开启、未装配自动取消）。
 * 实现方自带话术与签名策略，IAM 只交付验证码/用途/有效期，不认知具体短信渠道。
 *
 * @author surezzzzzz
 */
public interface SmsDeliveryProvider {

    /**
     * 投递验证码短信。失败以 RuntimeException 抛出（实现方自定义异常类型），
     * 调用方以此判定本次发送失败；IAM 不自动重试。
     *
     * @param phone            目标手机号（E.164）
     * @param code             验证码明文（仅投递链内可见，禁入日志）
     * @param purpose          用途（取值见 {@link io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant} 的 SMS_PURPOSE_* 常量；决定话术模板）
     * @param expiresInSeconds 挑战有效期（秒，服务端配置唯一事实源；话术以 {minutes} 占位消费）
     */
    void deliver(String phone, String code, String purpose, int expiresInSeconds);

    /**
     * 能力声明：宣称支持的区号前缀（E.164 带 +，如 "+86"）。
     * 入口校验与前端区号列表统一消费本声明——adaptor 自述范围，IAM 不认知具体地域。
     *
     * @return 宣称支持的区号前缀列表（只读语义：调用方不得修改返回列表）
     */
    List<String> supportedRegions();
}
