package io.github.surezzzzzz.sdk.feedback.server.constant;

import lombok.Getter;

/**
 * 反馈发起形态枚举。
 *
 * <p>统计提示字段（修正④）：intercepted 值来自 web 深链参数声明，服务端无法也不校验其来源真伪，
 * 不做安全语义，仅用于管理侧区分处理优先级。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum FeedbackKind {

    /**
     * 主动发起（提交入口）
     */
    PROACTIVE("proactive", "主动发起"),

    /**
     * 错误场景入口深链带入
     */
    INTERCEPTED("intercepted", "错误场景发起");

    private final String code;
    private final String description;

    FeedbackKind(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static FeedbackKind fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FeedbackKind kind : values()) {
            if (kind.code.equalsIgnoreCase(code)) {
                return kind;
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
        FeedbackKind[] kinds = values();
        String[] codes = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            codes[i] = kinds[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
