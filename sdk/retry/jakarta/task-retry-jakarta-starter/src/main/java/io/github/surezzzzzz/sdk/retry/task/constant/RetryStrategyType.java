package io.github.surezzzzzz.sdk.retry.task.constant;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 重试策略类型
 *
 * @author surezzzzzz
 */
@Getter
public enum RetryStrategyType {

    EXPONENTIAL("exponential", "指数退避"),
    FIXED("fixed", "固定延迟"),
    NONE("none", "无延迟");

    private final String code;
    private final String description;

    RetryStrategyType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static RetryStrategyType fromCode(String code) {
        for (RetryStrategyType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static List<String> getAllCodes() {
        List<String> codes = new ArrayList<String>();
        for (RetryStrategyType type : values()) {
            codes.add(type.getCode());
        }
        return Collections.unmodifiableList(codes);
    }

    @Override
    public String toString() {
        return code;
    }
}
