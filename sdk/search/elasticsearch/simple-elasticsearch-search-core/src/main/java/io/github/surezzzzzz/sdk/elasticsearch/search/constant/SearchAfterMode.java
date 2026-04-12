package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

import lombok.Getter;

/**
 * search_after 翻页模式
 *
 * @author surezzzzzz
 */
@Getter
public enum SearchAfterMode {

    /**
     * 自动追加 _id ASC 作为 tiebreaker，防止非唯一排序字段导致丢数据（默认）
     * 兼容 v1.2.1 行为，注意：_id 排序会触发 ES fielddata，内存较小的集群慎用
     */
    TIEBREAKER("tiebreaker", "自动追加 _id tiebreaker"),

    /**
     * 使用 Point In Time 快照翻页，不追加 _id，需要 ES 7.10+
     * 适合内存敏感场景，同时提供快照一致性
     */
    PIT("pit", "PIT 快照翻页"),

    /**
     * 不追加任何 tiebreaker
     * 适合排序字段本身已唯一的场景（如按唯一 ID 排序）
     */
    NONE("none", "无 tiebreaker");

    private final String code;
    private final String description;

    SearchAfterMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举，null 时返回默认值 TIEBREAKER
     *
     * @param code 模式代码
     * @return 枚举值
     */
    public static SearchAfterMode fromCode(String code) {
        if (code == null) {
            return TIEBREAKER;
        }
        for (SearchAfterMode m : values()) {
            if (m.code.equalsIgnoreCase(code)) {
                return m;
            }
        }
        throw new IllegalArgumentException("不支持的 searchAfterMode: " + code);
    }

    /**
     * 判断 code 是否有效
     *
     * @param code 模式代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        for (SearchAfterMode m : values()) {
            if (m.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有有效的 code
     *
     * @return code 数组
     */
    public static String[] getAllCodes() {
        SearchAfterMode[] modes = values();
        String[] codes = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            codes[i] = modes[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
