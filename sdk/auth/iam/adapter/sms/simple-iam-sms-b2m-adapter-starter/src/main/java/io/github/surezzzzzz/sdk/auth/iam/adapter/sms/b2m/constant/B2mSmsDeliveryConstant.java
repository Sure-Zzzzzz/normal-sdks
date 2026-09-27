package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.constant;

import java.util.Collections;
import java.util.List;

/**
 * B2M 短信投递常量。
 *
 * @author surezzzzzz
 */
public final class B2mSmsDeliveryConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m";
    /**
     * 宣称支持的区号（B2M 平台仅国内通道；能力自述，出入口统一消费本声明）
     */
    public static final List<String> SUPPORTED_REGIONS = Collections.singletonList("+86");
    /**
     * 签名形态校验前缀（话术模板含【即视为覆盖签名,启动失败）
     */
    public static final String SIGNATURE_BRACKET_OPEN = "【";
    /**
     * 话术验证码占位符
     */
    public static final String CODE_PLACEHOLDER = "{code}";
    /**
     * 有效期分钟占位符（服务端 TTL 配置换算，话术单源消费）
     */
    public static final String MINUTES_PLACEHOLDER = "{minutes}";
    /**
     * 默认话术（登录/绑定/忘记密码）
     */
    public static final String DEFAULT_TEXT_LOGIN = "您的登录验证码：{code}，{minutes}分钟内有效，请注意身份保密。";
    public static final String DEFAULT_TEXT_BIND = "您正在绑定手机号，验证码：{code}，{minutes}分钟内有效，请注意身份保密。";
    public static final String DEFAULT_TEXT_FORGOT_PASSWORD = "您正在重置密码，验证码：{code}，{minutes}分钟内有效，请注意身份保密。";
    private B2mSmsDeliveryConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
