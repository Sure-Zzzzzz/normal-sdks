package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

import lombok.Getter;

/**
 * 时间范围枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum TimeRange {

    /**
     * 近5分钟
     */
    LAST_5_MINUTES("last_5_minutes", "近5分钟"),

    /**
     * 近1小时
     */
    LAST_HOUR("last_hour", "近1小时"),

    /**
     * 近24小时
     */
    LAST_24_HOURS("last_24_hours", "近24小时"),

    /**
     * 近7天
     */
    LAST_7_DAYS("last_7_days", "近7天"),

    /**
     * 近30天
     */
    LAST_30_DAYS("last_30_days", "近30天"),

    /**
     * 近3个月
     */
    LAST_3_MONTHS("last_3_months", "近3个月"),

    /**
     * 近一年
     */
    LAST_YEAR("last_year", "近一年"),

    /**
     * 今天
     */
    TODAY("today", "今天"),

    /**
     * 昨天
     */
    YESTERDAY("yesterday", "昨天"),

    /**
     * 本周
     */
    THIS_WEEK("this_week", "本周"),

    /**
     * 本月
     */
    THIS_MONTH("this_month", "本月");

    private final String code;
    private final String description;

    TimeRange(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static TimeRange fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TimeRange type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
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
        TimeRange[] types = values();
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
