package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

import lombok.Getter;

/**
 * 索引降级级别枚举
 * 用于处理 HTTP 请求行过长导致的 too_long_frame_exception
 *
 * @author surezzzzzz
 */
@Getter
public enum DowngradeLevel {

    /**
     * 不降级，使用具体索引名
     */
    LEVEL_0(0, "不降级，使用具体索引名"),

    /**
     * 一级降级，使用更粗粒度的通配符
     */
    LEVEL_1(1, "一级降级，使用更粗粒度的通配符"),

    /**
     * 二级降级，使用更粗粒度的通配符
     */
    LEVEL_2(2, "二级降级，使用更粗粒度的通配符"),

    /**
     * 三级降级，使用最粗粒度的通配符
     */
    LEVEL_3(3, "三级降级，使用最粗粒度的通配符");

    private final int value;
    private final String description;

    DowngradeLevel(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 判断是否还有下一降级级别
     *
     * @return true: 有下一级别, false: 已是最大级别
     */
    public boolean hasNext() {
        return this.value < LEVEL_3.value;
    }

    /**
     * 获取下一降级级别
     * <p>
     * 用于在降级重试时逐级提升降级级别。
     * 如果已达到最大级别（LEVEL_3），则返回自身，保证幂等性避免NPE。
     * </p>
     *
     * @return 下一降级级别。如果已是最大级别（LEVEL_3），则返回自身
     */
    public DowngradeLevel next() {
        if (!hasNext()) {
            return this;  // 已达最大级别，返回自身保证幂等性
        }
        return values()[this.value + 1];
    }

    /**
     * 从数值转换为枚举
     *
     * @param value 降级级别值
     * @return 对应的枚举
     */
    public static DowngradeLevel fromValue(int value) {
        for (DowngradeLevel level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        throw new IllegalArgumentException("Invalid DowngradeLevel value: " + value);
    }
}
