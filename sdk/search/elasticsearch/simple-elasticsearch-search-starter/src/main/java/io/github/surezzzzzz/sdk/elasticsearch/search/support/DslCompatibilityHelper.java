package io.github.surezzzzzz.sdk.elasticsearch.search.support;

/**
 * DSL 兼容性处理工具
 * 处理不同 ES 版本间的 DSL 差异
 *
 * @author surezzzzzz
 */
public final class DslCompatibilityHelper {

    private DslCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 移除 ES 7.x 特有的 composite 聚合字段（missing_bucket），保证 ES 6.x 兼容性
     * <p>
     * ES 7.x Java client 序列化 composite source 时会带上 missing_bucket 字段，
     * 而 ES 6.x 不认识该字段，会返回 parsing_exception。
     *
     * @param dsl 原始 DSL JSON 字符串
     * @return 移除 ES 7.x 特有字段后的 DSL
     */
    public static String removeEs7OnlyCompositeFields(String dsl) {
        if (dsl == null || !dsl.contains("missing_bucket")) {
            return dsl;
        }
        return dsl
                .replaceAll(",\\s*\"missing_bucket\"\\s*:\\s*(true|false)", "")
                .replaceAll("\"missing_bucket\"\\s*:\\s*(true|false)\\s*,", "")
                .replaceAll("\"missing_bucket\"\\s*:\\s*(true|false)", "");
    }
}
