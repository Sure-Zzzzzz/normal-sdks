package io.github.surezzzzzz.sdk.elasticsearch.search.expression;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.invalid;

/**
 * 滚动时间范围的截止方式；历史完整日、周、月、季度不受此选项影响。
 */
@Getter
public enum TimeRangeEnd {
    NOW("now", "锚点时刻"),
    TODAY_START("today_start", "锚点所在日的零点");

    @JsonValue
    private final String code;
    private final String description;

    TimeRangeEnd(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 未知码返回 null，不回退成其他模式。
     */
    public static TimeRangeEnd fromCode(String code) {
        for (TimeRangeEnd value : values()) if (value.code.equals(code)) return value;
        return null;
    }

    /**
     * JSON 只接受明确的稳定代码。
     */
    @JsonCreator
    public static TimeRangeEnd parse(String code) {
        TimeRangeEnd value = fromCode(code);
        if (value == null) throw invalid();
        return value;
    }

    /**
     * 判断是否为已知稳定代码。
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 返回独立代码数组。
     */
    public static String[] getAllCodes() {
        return Arrays.stream(values()).map(TimeRangeEnd::getCode).toArray(String[]::new);
    }

    /**
     * 日志和展示统一使用稳定代码。
     */
    @Override
    public String toString() {
        return code;
    }
}
