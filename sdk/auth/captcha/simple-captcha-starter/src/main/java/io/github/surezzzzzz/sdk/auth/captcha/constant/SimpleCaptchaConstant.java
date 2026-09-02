package io.github.surezzzzzz.sdk.auth.captcha.constant;

/**
 * Simple Captcha Constants
 *
 * @author surezzzzzz
 */
public final class SimpleCaptchaConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.captcha";

    // ==================== 配置相关常量 ====================
    /**
     * 默认挑战有效期（秒）
     */
    public static final long DEFAULT_CHALLENGE_TTL_SECONDS = 120L;
    /**
     * 默认图片码字符个数
     */
    public static final int DEFAULT_IMAGE_CHAR_LENGTH = 4;
    /**
     * 默认应用实例标识（多应用共用 Redis 时区分实例）
     */
    public static final String DEFAULT_ME = "default";
    /**
     * 验证码图片编码失败
     */
    public static final String ERROR_CODE_IMAGE_ENCODE_FAILED = "CAPTCHA_001";

    // ==================== 错误码常量 ====================
    /**
     * Redis Key 前缀（SDK 标识）
     */
    public static final String KEY_PREFIX = "sure-auth-captcha";

    // ==================== Redis Key 相关常量 ====================
    /**
     * 业务类型：验证码挑战
     */
    public static final String BUSINESS_CHALLENGE = "challenge";
    /**
     * Redis Key 分隔符
     */
    public static final String SEPARATOR_COLON = ":";
    /**
     * Redis Key data-id 分隔符
     */
    public static final String SEPARATOR_DOUBLE_COLON = "::";
    /**
     * HashTag 包裹前缀
     */
    public static final String BRACE_PREFIX = "{";
    /**
     * HashTag 包裹后缀
     */
    public static final String BRACE_SUFFIX = "}";
    /**
     * 挑战类型：图片
     */
    public static final String CHALLENGE_TYPE_IMAGE = "image";

    // ==================== 图片码绘制相关常量 ====================
    /**
     * 图片码字符池（去混淆：剔除 0/O、1/I/l、易混字符）
     */
    public static final String IMAGE_CHAR_POOL = "abcdefghjkmnpqrstuvwxyz23456789";
    /**
     * 单字符占位宽度（像素）
     */
    public static final int IMAGE_WIDTH_PER_CHAR = 30;
    /**
     * 图片高度（像素）
     */
    public static final int IMAGE_HEIGHT = 42;
    /**
     * 图片左右留白（像素）
     */
    public static final int IMAGE_PADDING = 10;
    /**
     * 干扰线条数
     */
    public static final int INTERFERENCE_LINE_COUNT = 5;
    /**
     * 噪点个数
     */
    public static final int NOISE_POINT_COUNT = 30;
    /**
     * 图片输出格式
     */
    public static final String IMAGE_FORMAT_PNG = "png";
    /**
     * 挑战内容 data URI 模板（前端 img 标签直接使用）
     * 参数: base64 编码的图片内容
     */
    public static final String TEMPLATE_DATA_URI = "data:image/png;base64,%s";

    private SimpleCaptchaConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
