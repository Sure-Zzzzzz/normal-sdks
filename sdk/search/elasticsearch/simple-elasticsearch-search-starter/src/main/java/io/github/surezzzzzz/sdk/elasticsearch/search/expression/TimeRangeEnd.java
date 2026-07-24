package io.github.surezzzzzz.sdk.elasticsearch.search.expression;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 表达式时间范围截止模式
 *
 * @author surezzzzzz
 */
public enum TimeRangeEnd {

    /**
     * 截止到当前时间
     */
    NOW,

    /**
     * 截止到当天零点
     */
    TODAY_START;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TimeRangeEnd fromValue(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("timeRangeEnd 仅支持字符串枚举值");
        }
        return TimeRangeEnd.valueOf((String) value);
    }
}
