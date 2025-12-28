package io.github.surezzzzzz.sdk.sensitive.keyword.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================
    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";

    /**
     * 默认策略配置无效
     */
    public static final String CONFIG_DEFAULT_STRATEGY_INVALID = "CONFIG_002";

    /**
     * 关键词配置为空
     */
    public static final String CONFIG_KEYWORD_EMPTY = "CONFIG_003";

    /**
     * 关键词长度超限
     */
    public static final String CONFIG_KEYWORD_LENGTH_INVALID = "CONFIG_004";

    /**
     * 掩码类型无效
     */
    public static final String CONFIG_MASK_TYPE_INVALID = "CONFIG_006";

    /**
     * 占位符格式无效
     */
    public static final String CONFIG_PLACEHOLDER_INVALID = "CONFIG_007";

    /**
     * 策略配置缺失
     */
    public static final String CONFIG_STRATEGY_MISSING = "CONFIG_008";

    /**
     * NLP配置无效
     */
    public static final String CONFIG_NLP_INVALID = "CONFIG_009";

    // ==================== 资源错误 ====================
    /**
     * 内置资源加载失败
     */
    public static final String RESOURCE_LOAD_FAILED = "RESOURCE_001";

    // ==================== 脱敏错误 ====================
    /**
     * 脱敏处理失败
     */
    public static final String MASK_PROCESS_FAILED = "MASK_001";

    /**
     * 匹配器初始化失败
     */
    public static final String MASK_MATCHER_INIT_FAILED = "MASK_003";

    /**
     * NLP处理失败
     */
    public static final String MASK_NLP_FAILED = "MASK_004";

    /**
     * 哈希计算失败
     */
    public static final String MASK_HASH_FAILED = "MASK_005";

    // ==================== NLP错误 ====================
    /**
     * NLP提供者未配置
     */
    public static final String NLP_PROVIDER_NOT_CONFIGURED = "NLP_001";

    /**
     * NLP模型加载失败
     */
    public static final String NLP_MODEL_LOAD_FAILED = "NLP_003";
}
