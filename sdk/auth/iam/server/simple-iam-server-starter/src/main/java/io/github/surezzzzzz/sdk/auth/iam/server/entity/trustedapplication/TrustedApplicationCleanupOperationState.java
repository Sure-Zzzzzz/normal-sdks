package io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication;

import lombok.Getter;

/**
 * 可信应用异步删除操作状态。
 *
 * <p>FAILED 只能由管理端显式重新入队；操作本身永不表示应用已恢复。
 * code 与常量名同形，持久化列与管理端展示的值保持稳定。
 *
 * @author surezzzzzz
 */
@Getter
public enum TrustedApplicationCleanupOperationState {

    /**
     * 已入队待处理。
     */
    PENDING("PENDING", "已入队待处理"),

    /**
     * 执行中。
     */
    RUNNING("RUNNING", "执行中"),

    /**
     * 失败待重试。
     */
    RETRYING("RETRYING", "失败待重试"),

    /**
     * 已完成。
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 失败待人工介入。
     */
    FAILED("FAILED", "失败待人工介入");

    /**
     * 用于持久化与管理端传输的稳定编码。
     */
    private final String code;

    /**
     * 面向展示的中文说明，不应用于程序分支。
     */
    private final String description;

    TrustedApplicationCleanupOperationState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举。
     *
     * @param code 状态代码
     * @return 枚举，如果不存在返回 null
     */
    public static TrustedApplicationCleanupOperationState fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TrustedApplicationCleanupOperationState type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 校验 code 是否有效。
     *
     * @param code 状态代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的状态代码。
     *
     * @return 状态代码数组
     */
    public static String[] getAllCodes() {
        TrustedApplicationCleanupOperationState[] types = values();
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
