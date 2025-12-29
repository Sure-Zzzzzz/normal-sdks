package io.github.surezzzzzz.sdk.sensitive.keyword.constant;

/**
 * Configuration Constants
 *
 * @author surezzzzzz
 */
public final class SmartKeywordSensitiveConstant {

    private SmartKeywordSensitiveConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.sensitive.keyword";

    /**
     * 默认掩码字符
     */
    public static final String DEFAULT_MASK_CHAR = "*";

    /**
     * 占位符左括号
     */
    public static final String PLACEHOLDER_LEFT_BRACKET = "[";

    /**
     * 占位符右括号
     */
    public static final String PLACEHOLDER_RIGHT_BRACKET = "]";

    /**
     * 占位符分隔符
     */
    public static final String PLACEHOLDER_SEPARATOR = "_";

    /**
     * 默认占位符前缀
     */
    public static final String DEFAULT_PLACEHOLDER_PREFIX = "[ORG_";

    /**
     * 默认占位符后缀
     */
    public static final String DEFAULT_PLACEHOLDER_SUFFIX = "]";

    /**
     * 默认占位符序号
     */
    public static final String DEFAULT_PLACEHOLDER_INDEX = "001";

    /**
     * 默认占位符（fallback时使用）
     */
    public static final String DEFAULT_PLACEHOLDER_FALLBACK = "[机构_001]";

    // ==================== 组织类型分类 Map ====================

    /**
     * 组织类型分类Map（从org-types.txt加载）
     * key=组织类型（如"有限公司"）, value=分类标签（如"企业"）
     * 用于PlaceholderMaskStrategy快速判断组织类型分类
     */
    public static final java.util.Map<String, String> ORG_TYPE_CATEGORY_MAP =
            io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader.loadOrgTypeCategoryMap();

    /**
     * 组织类型分类标签 - 企业
     */
    public static final String ORG_TYPE_CATEGORY_COMPANY = "企业";

    /**
     * 组织类型分类标签 - 政府
     */
    public static final String ORG_TYPE_CATEGORY_GOVERNMENT = "政府";

    /**
     * 组织类型分类标签 - 学校
     */
    public static final String ORG_TYPE_CATEGORY_SCHOOL = "学校";

    /**
     * 组织类型分类标签 - 医疗
     */
    public static final String ORG_TYPE_CATEGORY_MEDICAL = "医疗";

    /**
     * 组织类型分类标签 - 金融
     */
    public static final String ORG_TYPE_CATEGORY_FINANCE = "金融";

    /**
     * 组织类型前缀 - 企业（与原来的 ORG_TYPE_PREFIX_COMPANY 对应）
     * 注：使用"企业"而非"公司"，因为包含合伙企业、个人独资企业等非公司制企业
     */
    public static final String ORG_TYPE_PREFIX_COMPANY = "企业";

    /**
     * 组织类型前缀 - 单位
     */
    public static final String ORG_TYPE_PREFIX_ORGANIZATION = "单位";

    /**
     * 组织类型前缀 - 学校
     */
    public static final String ORG_TYPE_PREFIX_SCHOOL = "学校";

    /**
     * 组织类型前缀 - 医疗
     */
    public static final String ORG_TYPE_PREFIX_MEDICAL = "医疗";

    /**
     * 组织类型前缀 - 金融
     */
    public static final String ORG_TYPE_PREFIX_FINANCE = "金融";

    /**
     * 组织类型前缀 - 机构（默认）
     */
    public static final String ORG_TYPE_PREFIX_DEFAULT = "机构";

    /**
     * 内置资源路径前缀
     */
    public static final String BUILT_IN_RESOURCE_PATH = "built-in/";

    /**
     * 内置NLP提供者类型
     */
    public static final String NLP_PROVIDER_BUILT_IN = "BUILT_IN";

    /**
     * 地域词典文件名
     */
    public static final String REGION_DICT_FILE = "regions.txt";

    /**
     * 组织类型词典文件名
     */
    public static final String ORG_TYPE_DICT_FILE = "org-types.txt";

    /**
     * 行业关键词词典文件名
     */
    public static final String INDUSTRY_DICT_FILE = "industry-keywords.txt";

    /**
     * 品牌词典文件名
     */
    public static final String BRAND_DICT_FILE = "brand-keywords.txt";

    /**
     * 默认掩码类型（使用MaskType枚举的code值）
     */
    public static final String DEFAULT_MASK_TYPE = MaskType.ASTERISK.getCode();

    /**
     * 固定星号数量（keep-length=false时使用）
     */
    public static final int DEFAULT_FIXED_MASK_LENGTH = 3;

    /**
     * 括号内容固定星号数量（未配置时继承自fixed-mask-length）
     */
    public static final Integer DEFAULT_FIXED_BRACKET_MASK_LENGTH = null;  // null表示继承fixedMaskLength

    /**
     * NLP超时时间（毫秒）
     */
    public static final int DEFAULT_NLP_TIMEOUT_MS = 1000;

    /**
     * 最小关键词长度
     */
    public static final int MIN_KEYWORD_LENGTH = 1;

    /**
     * 最大关键词长度
     */
    public static final int MAX_KEYWORD_LENGTH = 200;

    /**
     * 最小组织名长度
     */
    public static final int MIN_ORG_LENGTH = 4;

    /**
     * 最大组织名长度
     */
    public static final int MAX_ORG_LENGTH = 50;

    /**
     * 哈希算法
     */
    public static final String HASH_ALGORITHM = "MD5";

    /**
     * 哈希结果截取长度
     */
    public static final int HASH_LENGTH = 32;

    /**
     * Fallback脱敏短文本长度阈值
     */
    public static final int FALLBACK_LENGTH_THRESHOLD_SHORT = 3;

    /**
     * Fallback脱敏中文本长度阈值
     */
    public static final int FALLBACK_LENGTH_THRESHOLD_MEDIUM = 7;

    /**
     * Fallback脱敏长文本长度阈值
     */
    public static final int FALLBACK_LENGTH_THRESHOLD_LONG = 11;

    /**
     * Fallback脱敏保留字符数（长度≤3时）
     */
    public static final int FALLBACK_KEEP_CHARS_SHORT = 1;

    /**
     * Fallback脱敏保留字符数（长度4-7时）
     */
    public static final int FALLBACK_KEEP_CHARS_MEDIUM = 1;

    /**
     * Fallback脱敏保留字符数（长度8-11时）
     */
    public static final int FALLBACK_KEEP_CHARS_LONG = 2;

    /**
     * Fallback脱敏保留字符数（长度≥12时）
     */
    public static final int FALLBACK_KEEP_CHARS_EXTRA_LONG = 3;

    // ==================== 保留率降级策略相关常量 ====================
    // 注：机构类型的保留率阈值已定义在 SensitiveOrgType 枚举中，通过 getRetentionThreshold() 获取

    /**
     * 二级降级阈值（第一级降级后如果保留率仍>=此值，触发第二级降级）
     */
    public static final double SECOND_DOWNGRADE_THRESHOLD = 0.60;

    /**
     * 金融机构关键词（从industry-keywords.txt动态提取value="金融"的关键词）
     * 用于MaskStrategyHelper判断是否金融机构，决定保留率阈值（60%）
     */
    public static final java.util.Set<String> FINANCIAL_KEYWORDS =
            io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader.extractKeywordsByIndustry("金融");

    /**
     * 政府机构关键词（从industry-keywords.txt动态提取value包含"政府|党政|公安政法"的关键词）
     * 用于MaskStrategyHelper判断是否政府机构，决定保留率阈值（60%）
     */
    public static final java.util.Set<String> GOVERNMENT_KEYWORDS =
            io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader.extractKeywordsByIndustry(
                    "政府", "党政", "公安政法", "外交"
            );

    /**
     * 教育机构关键词（从industry-keywords.txt动态提取value="教育"的关键词）
     * 用于MaskStrategyHelper判断是否教育机构，决定保留率阈值（70%）
     */
    public static final java.util.Set<String> EDUCATION_KEYWORDS =
            io.github.surezzzzzz.sdk.sensitive.keyword.support.ResourceLoader.extractKeywordsByIndustry("教育");

    // ==================== 地域识别相关常量 ====================

    /**
     * 非地域词黑名单（地名+设施、品牌名等）
     * 这些词虽然包含地名，但整体不是地域概念
     */
    public static final java.util.Set<String> NON_REGION_BLACKLIST = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "天津港", "上海港", "青岛港", "宁波港", "广州港", "深圳港",  // 港口
                    "红岩", "海城",  // 品牌或易混淆词
                    "林县"  // 避免匹配"上林县"时错误匹配
            ))
    );

    // ==================== 组织类型提取相关常量 ====================

    /**
     * 组织类型最小长度（避免匹配单个字符）
     */
    public static final int MIN_ORG_TYPE_LENGTH = 2;

    // ==================== 提取过程叙述相关常量 ====================

    /**
     * 叙述：规则引擎开始
     */
    public static final String NARRATIVE_RULE_START = "规则引擎从\"";

    /**
     * 叙述：中
     */
    public static final String NARRATIVE_IN = "\"中";

    /**
     * 叙述：依次
     */
    public static final String NARRATIVE_IN_ORDER = "依次";

    /**
     * 叙述：提取地域
     */
    public static final String NARRATIVE_EXTRACT_REGION = "提取地域\"";

    /**
     * 叙述：品牌
     */
    public static final String NARRATIVE_BRAND = "品牌\"";

    /**
     * 叙述：组织类型
     */
    public static final String NARRATIVE_ORG_TYPE = "组织类型\"";

    /**
     * 叙述：行业关键词
     */
    public static final String NARRATIVE_INDUSTRY_KEYWORD = "行业关键词\"";

    /**
     * 叙述：剩余文本为
     */
    public static final String NARRATIVE_REMAINING_TEXT = "，剩余文本为\"";

    /**
     * 叙述：未找到行业关键词
     */
    public static final String NARRATIVE_NO_INDUSTRY = "，未找到行业关键词";

    /**
     * 叙述：未提取到任何元信息
     */
    public static final String NARRATIVE_NO_META = "未提取到任何元信息";

    /**
     * 叙述：NLP分析完整文本
     */
    public static final String NARRATIVE_NLP_ANALYZE = " NLP分析完整文本";

    /**
     * 叙述：地域
     */
    public static final String NARRATIVE_REGION = "地域\"";

    /**
     * 叙述：行业
     */
    public static final String NARRATIVE_INDUSTRY = "行业\"";

    /**
     * 叙述：关键词标签
     */
    public static final String NARRATIVE_KEYWORD_LABEL = "\"(关键词:";

    /**
     * 叙述：识别出
     */
    public static final String NARRATIVE_RECOGNIZED = "识别出";

    /**
     * 叙述：但未发现新的元信息
     */
    public static final String NARRATIVE_NO_NEW_META = "但未发现新的元信息。";

    /**
     * 叙述：NLP提取失败
     */
    public static final String NARRATIVE_NLP_FAILED = " NLP提取失败，仅使用规则引擎结果。";

    /**
     * 叙述：最终采用
     */
    public static final String NARRATIVE_FINAL_RESULT = " 最终采用：";

    /**
     * 叙述：规则+NLP一致
     */
    public static final String NARRATIVE_RULE_NLP_MATCH = "(规则+NLP一致)";

    /**
     * 叙述：规则
     */
    public static final String NARRATIVE_RULE = "(规则)";

    /**
     * 叙述：NLP
     */
    public static final String NARRATIVE_NLP = "(NLP)";

    /**
     * 叙述：规则+NLP
     */
    public static final String NARRATIVE_RULE_NLP = "(规则+NLP)";

    /**
     * 叙述：无有效元信息
     */
    public static final String NARRATIVE_NO_VALID_META = "无有效元信息。";

    /**
     * 叙述：引号
     */
    public static final String NARRATIVE_QUOTE = "\"";

    /**
     * 叙述：右括号
     */
    public static final String NARRATIVE_RIGHT_PAREN = ")";

    /**
     * 叙述：句号
     */
    public static final String NARRATIVE_PERIOD = "。";

    /**
     * 叙述：顿号
     */
    public static final String NARRATIVE_COMMA = "、";

    /**
     * 叙述：加号分隔
     */
    public static final String NARRATIVE_PLUS = " + ";

    /**
     * 叙述：中文逗号（用于连接列表项）
     */
    public static final String NARRATIVE_CHINESE_COMMA = "，";

    // ==================== 策略字段名称常量 ====================

    /**
     * 策略字段名：地域
     */
    public static final String FIELD_NAME_REGION = "地域";

    /**
     * 策略字段名：行业
     */
    public static final String FIELD_NAME_INDUSTRY = "行业";

    /**
     * 策略字段名：品牌
     */
    public static final String FIELD_NAME_BRAND = "品牌";

    /**
     * 策略字段名：组织类型
     */
    public static final String FIELD_NAME_ORG_TYPE = "组织类型";

    /**
     * 策略字段名：括号
     */
    public static final String FIELD_NAME_BRACKET = "括号";

    /**
     * 策略字段名：长度信息
     */
    public static final String FIELD_NAME_LENGTH = "长度信息";

    // ==================== 元信息提取结果标签常量 ====================

    /**
     * 规则引擎提取结果标签
     */
    public static final String LABEL_RULE_BASED_EXTRACTION = "\n[规则引擎提取: ";

    /**
     * NLP提取结果标签
     */
    public static final String LABEL_NLP_EXTRACTION = "\n[NLP提取: ";

    // ==================== 脱敏过程叙述相关常量 ====================

    /**
     * 模板：叙述章节标题 "\n%d. %s"
     * 参数: 序号, 标题
     */
    public static final String TEMPLATE_NARRATIVE_SECTION = "\n%d. %s";

    /**
     * 叙述标题：元信息提取过程
     */
    public static final String NARRATIVE_SECTION_TITLE_META_EXTRACTION = "元信息提取过程：";

    /**
     * 叙述标题：脱敏策略决策过程
     */
    public static final String NARRATIVE_SECTION_TITLE_STRATEGY_DECISION = "脱敏策略决策过程：";

    /**
     * 叙述标题：兜底脱敏决策
     */
    public static final String NARRATIVE_SECTION_TITLE_FALLBACK_DECISION = "兜底脱敏决策：";

    /**
     * 叙述标题：最终结果
     */
    public static final String NARRATIVE_SECTION_TITLE_FINAL_RESULT = "最终结果：";

    /**
     * 叙述：其中
     */
    public static final String NARRATIVE_AMONG = "，其中";

    /**
     * 叙述：原脱敏策略
     */
    public static final String NARRATIVE_ORIGINAL_STRATEGY = "原脱敏策略";

    /**
     * 叙述：保留
     */
    public static final String NARRATIVE_KEEP = "保留";

    /**
     * 叙述：不保留
     */
    public static final String NARRATIVE_NOT_KEEP = "不保留";

    /**
     * 叙述：最终策略
     */
    public static final String NARRATIVE_FINAL_STRATEGY = "，最终策略";

    /**
     * 叙述：但无可降级项
     */
    public static final String NARRATIVE_NO_DOWNGRADE_OPTION = "，但无可降级项";

    /**
     * 叙述：维持原策略
     */
    public static final String NARRATIVE_KEEP_ORIGINAL = "，维持原策略";

    /**
     * 叙述：未触发降级
     */
    public static final String NARRATIVE_NO_DOWNGRADE = "未触发降级";

    /**
     * 叙述：触发兜底脱敏
     */
    public static final String NARRATIVE_FALLBACK_TRIGGERED = "触发兜底脱敏：";

    /**
     * 叙述：未触发兜底脱敏
     */
    public static final String NARRATIVE_NO_FALLBACK = "未触发兜底脱敏：";

    /**
     * 叙述：无有效元信息
     */
    public static final String NARRATIVE_NO_VALID_META_INFO = "无有效元信息";

    /**
     * 叙述：保留字符为0
     */
    public static final String NARRATIVE_ZERO_RETENTION = "保留字符=0（完全脱敏）";

    /**
     * 叙述：正常脱敏
     */
    public static final String NARRATIVE_NORMAL_MASK = "正常脱敏";

    /**
     * 叙述：箭头
     */
    public static final String NARRATIVE_ARROW = " → ";

    /**
     * 叙述：分隔线（用于分隔自然语言和结构化数据）
     */
    public static final String NARRATIVE_SEPARATOR = "\n\n========== 详细数据 ==========";

    // ==================== 模板常量 ====================

    /**
     * 模板：主体文本长度信息 " 主体文本长度{0}字符"
     * 参数: {0} = mainLength
     */
    public static final String TEMPLATE_MAIN_LENGTH = " 主体文本长度%d字符";

    /**
     * 模板：字段详情 "{0}\"{1}\"{2}字符"
     * 参数: {0} = 字段名, {1} = 字段值, {2} = 长度
     */
    public static final String TEMPLATE_FIELD_DETAIL = "%s\"%s\"%d字符";

    /**
     * 模板：按原策略保留 "，按原策略共保留{0}字符，保留率{1}%"
     * 参数: {0} = retainedChars, {1} = retentionRate
     */
    public static final String TEMPLATE_ORIGINAL_RETENTION = "，按原策略共保留%d字符，保留率%.1f%%";

    /**
     * 模板：机构类型判定 "。机构类型判定为\"{0}\"，阈值{1}%"
     * 参数: {0} = orgType, {1} = threshold
     */
    public static final String TEMPLATE_ORG_TYPE_THRESHOLD = "。机构类型判定为\"%s\"，阈值%.0f%%";

    /**
     * 模板：最终保留 "，最终保留{0}字符，保留率{1}%"
     * 参数: {0} = finalRetainedChars, {1} = finalRetentionRate
     */
    public static final String TEMPLATE_FINAL_RETENTION = "，最终保留%d字符，保留率%.1f%%";

    /**
     * 模板：保留率未达到阈值 " 保留率{0}%未达到阈值"
     * 参数: {0} = finalRetentionRate
     */
    public static final String TEMPLATE_RATE_NOT_REACH = " 保留率%.1f%%未达到阈值";

    /**
     * 模板：保留率已达到阈值 " 保留率{0}%已达到阈值"
     * 参数: {0} = finalRetentionRate
     */
    public static final String TEMPLATE_RATE_REACHED = " 保留率已达到阈值";

    // ==================== KeywordSensitiveMaskHelper 相关常量 ====================

    /**
     * 识别来源类型：配置文件
     */
    public static final String SOURCE_TYPE_CONFIG = "CONFIG";

    /**
     * 消息：空文本，无需脱敏
     */
    public static final String MESSAGE_EMPTY_TEXT = "空文本，无需脱敏";

    /**
     * 消息：无匹配结果，应用fallback策略
     */
    public static final String MESSAGE_NO_MATCH_FALLBACK = "无匹配结果，应用fallback策略";

    /**
     * 消息：脱敏结果与原文相同，应用fallback策略
     */
    public static final String MESSAGE_RESULT_SAME_FALLBACK = "脱敏结果与原文相同，应用fallback策略";

    /**
     * 消息：无需调整
     */
    public static final String MESSAGE_NO_ADJUSTMENT = "无需调整";

    /**
     * 消息：保留率低于阈值，无需降级
     */
    public static final String MESSAGE_RETENTION_BELOW_THRESHOLD = "保留率低于阈值，无需降级";

    /**
     * 模板：识别阶段统计 "组织%d个%s | 配置关键词%d个%s"
     * 参数: 组织数量, 组织列表, 配置关键词数量, 关键词列表
     */
    public static final String TEMPLATE_RECOGNIZE_STATS = "组织%d个%s | 配置关键词%d个%s";

    /**
     * 模板：处理任务 "处理%d个任务: "
     * 参数: 任务数量
     */
    public static final String TEMPLATE_PROCESS_TASKS = "处理%d个任务: ";

    /**
     * 模板：脱敏成功 "成功 - 长度: %d → %d"
     * 参数: 原始长度, 脱敏后长度
     */
    public static final String TEMPLATE_MASK_SUCCESS = "成功 - 长度: %d → %d";

    /**
     * 模板：第一级降级 "第一级降级(关闭地域保留) %.1f%% → %.1f%%"
     * 参数: 原保留率, 降级后保留率
     */
    public static final String TEMPLATE_FIRST_DOWNGRADE = "第一级降级(关闭地域保留) %.1f%% → %.1f%%";

    /**
     * 模板：第二级降级 "; 第二级降级(关闭行业保留) %.1f%% → %.1f%%"
     * 参数: 原保留率, 降级后保留率
     */
    public static final String TEMPLATE_SECOND_DOWNGRADE = "; 第二级降级(关闭行业保留) %.1f%% → %.1f%%";

    /**
     * 模板：第三级降级 "; 第三级降级(关闭组织类型保留) %.1f%% → %.1f%%"
     * 参数: 原保留率, 降级后保留率
     */
    public static final String TEMPLATE_THIRD_DOWNGRADE = "; 第三级降级(关闭组织类型保留) %.1f%% → %.1f%%";

    /**
     * 模板：实体和来源格式 "%s[%s]"
     * 参数: 实体名称, 识别来源
     */
    public static final String TEMPLATE_ENTITY_SOURCE = "%s[%s]";

    /**
     * 分隔符：冒号+空格 ": "
     */
    public static final String SEPARATOR_COLON_SPACE = ": ";

    /**
     * 分隔符：逗号+空格 ", "
     */
    public static final String SEPARATOR_COMMA_SPACE = ", ";

    /**
     * 分隔符：空格+竖线+空格 " | "
     */
    public static final String SEPARATOR_PIPE = " | ";

    // ==================== MaskReasonHelper 简化格式模板 ====================

    /**
     * 模板：Hash脱敏说明 "\n使用Hash脱敏：直接对关键词进行%s哈希作为脱敏结果。"
     */
    public static final String TEMPLATE_HASH_MASK_DESC = "\n使用Hash脱敏：直接对关键词进行%s哈希作为脱敏结果。";

    /**
     * 消息：Placeholder脱敏前缀
     */
    public static final String MESSAGE_PLACEHOLDER_PREFIX = "\n使用Placeholder脱敏：";

    /**
     * 模板：Placeholder提取组织类型 "提取组织类型\"%s\"，生成占位符。"
     */
    public static final String TEMPLATE_PLACEHOLDER_WITH_ORG = "提取组织类型\"%s\"，生成占位符。";

    /**
     * 模板：Placeholder使用行业 "未提取到组织类型，使用行业\"%s\"生成占位符。"
     */
    public static final String TEMPLATE_PLACEHOLDER_WITH_INDUSTRY = "未提取到组织类型，使用行业\"%s\"生成占位符。";

    /**
     * 消息：Placeholder使用默认值
     */
    public static final String MESSAGE_PLACEHOLDER_DEFAULT = "未提取到元信息，使用默认占位符。";

    /**
     * 模板：简化格式最终结果 "\n最终结果：%s → %s"
     */
    public static final String TEMPLATE_SIMPLIFIED_RESULT = "\n最终结果：%s → %s";
}
