package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 查询配置默认值和固定协议边界。
 */
public final class SimpleElasticsearchSearchConstant {
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.elasticsearch.search";
    public static final String CONFIG_ENABLE = "enable";
    public static final String DEFAULT_API_PATH = "/api";
    public static final int DEFAULT_SIZE = 20;
    public static final int DEFAULT_MAX_SIZE = 1000;
    public static final int DEFAULT_MAX_OFFSET = 10000;
    public static final int DEFAULT_MAX_INDICES = 366;
    public static final int DEFAULT_MAX_DEPTH = 32;
    public static final int DEFAULT_MAX_NODES = 1000;
    public static final int DEFAULT_MAPPING_CACHE_SIZE = 256;
    public static final int DEFAULT_REFRESH_SECONDS = 300;
    public static final String DEFAULT_DATE_RANGE = "30d";
    public static final String DEFAULT_DATE_PATTERN = "yyyy.MM.dd";
    public static final String DEFAULT_ZONE = "UTC";
    public static final boolean DEFAULT_CACHE_MAPPING = true;
    public static final boolean DEFAULT_LAZY_LOAD = true;
    public static final boolean DEFAULT_STRICT_DATE_FILTER = true;
    public static final String DEFAULT_KEEP_ALIVE = "1m";
    public static final String DEFAULT_MASK = "****";
    public static final int MAX_CURSOR_SECONDS = 3600;
    public static final int MAX_CURSOR_LENGTH = 131072;
    public static final int CURSOR_KEY_BYTES = 32;
    public static final int CURSOR_IV_BYTES = 12;
    public static final int CURSOR_TAG_BITS = 128;
    public static final String CURSOR_CIPHER = "AES/GCM/NoPadding";
    public static final String CURSOR_KEY_ALGORITHM = "AES";
    public static final String CURSOR_AAD = "es-search-cursor-v1";
    public static final String HASH_ALGORITHM = "SHA-256";
    public static final String SOURCE_STRUCTURED = "structured";
    public static final int DEFAULT_EXPRESSION_MAX_LENGTH = 16384;
    public static final int DEFAULT_EXPRESSION_MAX_TOKENS = 4096;
    public static final int MONTHS_PER_QUARTER = 3;

    private SimpleElasticsearchSearchConstant() {
    }
}
