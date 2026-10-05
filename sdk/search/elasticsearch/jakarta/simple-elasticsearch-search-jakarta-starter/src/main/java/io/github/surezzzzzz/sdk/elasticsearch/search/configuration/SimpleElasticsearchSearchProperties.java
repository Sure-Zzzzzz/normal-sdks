package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单一查询配置；连接地址、认证与连接生命周期只属于 Route。
 */
@Data
@ConfigurationProperties(SimpleElasticsearchSearchConstant.CONFIG_PREFIX)
public class SimpleElasticsearchSearchProperties {
    /**
     * 显式启用；不自动创建默认直连客户端。
     */
    private boolean enable;
    /**
     * 可访问的索引目录，不替代用户数据权限。
     */
    private List<IndexConfig> indices = new ArrayList<>();
    /**
     * 协议资源上限和严格日期范围策略。
     */
    private QueryLimits queryLimits = new QueryLimits();
    /**
     * 多实例共享游标密钥与保活策略。
     */
    private Cursor cursor = new Cursor();
    /**
     * 默认关闭的 Servlet 端点与分数返回策略。
     */
    private Api api = new Api();
    /**
     * 可选元数据定时刷新。
     */
    private MappingRefresh mappingRefresh = new MappingRefresh();
    /**
     * Java 和 HTTP 表达式入口共享的解析资源上限。
     */
    private ExpressionConfig expression = new ExpressionConfig();

    @Data
    public static class IndexConfig {
        /**
         * name 是实际索引表达式，alias 仅是应用层标识。
         */
        private String name, alias;
        private boolean dateSplit;
        private String datePattern = SimpleElasticsearchSearchConstant.DEFAULT_DATE_PATTERN;
        private String dateField;
        private String zoneId = SimpleElasticsearchSearchConstant.DEFAULT_ZONE;
        private boolean cacheMapping = SimpleElasticsearchSearchConstant.DEFAULT_CACHE_MAPPING;
        private boolean lazyLoad = SimpleElasticsearchSearchConstant.DEFAULT_LAZY_LOAD;
        /**
         * 调用方保证唯一且可排序的字段；不能使用 _id 冒充。
         */
        private String tiebreakerField;
        private List<SensitiveFieldConfig> sensitiveFields = new ArrayList<>();
        /**
         * 字段到标签列表；表达式只映射字段节点，不替换字符串值。
         */
        private Map<String, List<String>> fieldMapping = new LinkedHashMap<>();
    }

    @Data
    public static class SensitiveFieldConfig {
        /**
         * 固定字段路径及 forbidden/mask 策略。
         */
        private String field, strategy;
        /**
         * 保留首尾字符数量，默认完全掩码。
         */
        private Integer maskStart, maskEnd;
        private String maskPattern = SimpleElasticsearchSearchConstant.DEFAULT_MASK;
    }

    @Data
    public static class QueryLimits {
        private int defaultSize = SimpleElasticsearchSearchConstant.DEFAULT_SIZE;
        private int maxSize = SimpleElasticsearchSearchConstant.DEFAULT_MAX_SIZE;
        private int maxOffset = SimpleElasticsearchSearchConstant.DEFAULT_MAX_OFFSET;
        private int maxIndices = SimpleElasticsearchSearchConstant.DEFAULT_MAX_INDICES;
        private int maxDepth = SimpleElasticsearchSearchConstant.DEFAULT_MAX_DEPTH;
        private int maxNodes = SimpleElasticsearchSearchConstant.DEFAULT_MAX_NODES;
        private int mappingCacheSize = SimpleElasticsearchSearchConstant.DEFAULT_MAPPING_CACHE_SIZE;
        private boolean strictDateFilter = SimpleElasticsearchSearchConstant.DEFAULT_STRICT_DATE_FILTER;
        private boolean ignoreUnavailableIndices;
        private boolean allowFullScan;
        private String defaultDateRange = SimpleElasticsearchSearchConstant.DEFAULT_DATE_RANGE;
    }

    @Data
    public static class Cursor {
        /**
         * Base64 编码的 32 字节共享 AES 密钥；不得输出到日志。
         */
        @ToString.Exclude
        private String encryptionKey;
        private String keepAlive = SimpleElasticsearchSearchConstant.DEFAULT_KEEP_ALIVE;
    }

    @Data
    public static class Api {
        /**
         * 默认关闭，宿主须自行保护全部端点。
         */
        private boolean enabled;
        private String basePath = SimpleElasticsearchSearchConstant.DEFAULT_API_PATH;
        private boolean includeScore;
    }

    @Data
    public static class MappingRefresh {
        private boolean enabled;
        private int intervalSeconds = SimpleElasticsearchSearchConstant.DEFAULT_REFRESH_SECONDS;
    }

    @Data
    public static class ExpressionConfig {
        /**
         * UTF-16 长度与非 EOF 词法单元上限，不能关闭。
         */
        private int maxLength = SimpleElasticsearchSearchConstant.DEFAULT_EXPRESSION_MAX_LENGTH;
        private int maxTokens = SimpleElasticsearchSearchConstant.DEFAULT_EXPRESSION_MAX_TOKENS;
    }
}
