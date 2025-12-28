package io.github.surezzzzzz.sdk.sensitive.keyword.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================
    public static final String CONFIG_VALIDATION_FAILED = "配置验证失败";

    public static final String CONFIG_DEFAULT_STRATEGY_INVALID = "默认策略配置无效";

    public static final String CONFIG_KEYWORD_EMPTY = "关键词不能为空";

    public static final String CONFIG_KEYWORD_LENGTH_INVALID = "关键词长度必须在 %d 到 %d 之间，当前关键词：%s";

    public static final String CONFIG_MASK_TYPE_INVALID = "掩码类型 [%s] 无效，支持的类型：%s";

    public static final String CONFIG_PLACEHOLDER_INVALID = "占位符 [%s] 格式无效，不能为空";

    public static final String CONFIG_STRATEGY_MISSING = "关键词 [%s] 缺少策略配置";

    public static final String CONFIG_NLP_INVALID = "NLP配置无效：%s";

    // ==================== 资源错误 ====================
    public static final String RESOURCE_LOAD_FAILED = "加载资源文件失败：%s";

    // ==================== 脱敏错误 ====================
    public static final String MASK_PROCESS_FAILED = "脱敏处理失败";

    public static final String MASK_MATCHER_INIT_FAILED = "关键词匹配器初始化失败";

    public static final String MASK_NLP_FAILED = "NLP处理失败：%s";

    public static final String MASK_HASH_FAILED = "哈希计算失败：%s";

    // ==================== NLP错误 ====================
    public static final String NLP_PROVIDER_NOT_CONFIGURED = "NLP提供者未配置";

    public static final String NLP_MODEL_LOAD_FAILED = "NLP模型加载失败：%s";
}
