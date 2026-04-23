package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

import lombok.Getter;

/**
 * Pipeline 聚合类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum PipelineAggType {

    /**
     * 对 bucket 结果排序 + 分页（Top N）
     */
    BUCKET_SORT("bucket_sort", "桶排序"),

    /**
     * 过滤不满足条件的 bucket（HAVING 语义）
     */
    BUCKET_SELECTOR("bucket_selector", "桶过滤");

    private final String code;
    private final String description;

    PipelineAggType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举，不存在返回 null
     *
     * @param code 类型代码
     * @return 枚举，不存在返回 null
     */
    public static PipelineAggType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PipelineAggType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断 code 是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        PipelineAggType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
