package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartRedisLimiter 动态策略管理操作
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public enum SmartRedisLimiterManagementOperation {

    /**
     * 新增策略
     */
    CREATE("CREATE", "新增"),

    /**
     * 修改策略
     */
    UPDATE("UPDATE", "修改"),

    /**
     * 启用策略
     */
    ENABLE("ENABLE", "启用"),

    /**
     * 停用策略
     */
    DISABLE("DISABLE", "停用"),

    /**
     * 删除策略
     */
    DELETE("DELETE", "删除");

    /**
     * 操作编码
     */
    private final String code;

    /**
     * 操作描述
     */
    private final String description;

    /**
     * 根据操作编码获取管理操作
     *
     * @param code 操作编码
     * @return 管理操作，不存在时返回 null
     */
    public static SmartRedisLimiterManagementOperation fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SmartRedisLimiterManagementOperation operation : values()) {
            if (operation.code.equalsIgnoreCase(code)) {
                return operation;
            }
        }
        return null;
    }

    /**
     * 判断操作编码是否有效
     *
     * @param code 操作编码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有操作编码
     *
     * @return 操作编码数组
     */
    public static String[] getAllCodes() {
        SmartRedisLimiterManagementOperation[] operations = values();
        String[] codes = new String[operations.length];
        for (int i = 0; i < operations.length; i++) {
            codes[i] = operations[i].code;
        }
        return codes;
    }

    /**
     * 获取操作编码
     *
     * @return 操作编码
     */
    @Override
    public String toString() {
        return code;
    }
}
