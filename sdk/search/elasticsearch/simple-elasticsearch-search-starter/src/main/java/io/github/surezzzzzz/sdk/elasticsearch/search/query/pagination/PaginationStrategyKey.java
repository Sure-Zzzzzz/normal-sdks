package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

/**
 * 翻页策略 Key 常量
 *
 * <p>用于 {@link PaginationStrategyRegistry} 的注册和查找。
 * 格式：{@code type} 或 {@code type:mode}
 *
 * @author surezzzzzz
 */
public final class PaginationStrategyKey {

    /**
     * offset 分页
     */
    public static final String OFFSET = "offset";

    /**
     * search_after 分页 key 前缀
     */
    public static final String SEARCH_AFTER_PREFIX = "search_after:";

    /**
     * scroll 分页
     */
    public static final String SCROLL = "scroll";

    private PaginationStrategyKey() {
        throw new UnsupportedOperationException("Utility class");
    }
}
