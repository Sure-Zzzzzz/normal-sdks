package io.github.surezzzzzz.sdk.auth.authorization.application.core.constant;

import lombok.Getter;

/**
 * 应用授权主体类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum ApplicationAuthorizationSubjectType {

    /**
     * 人员主体。
     */
    HUMAN("HUMAN", "人员主体"),
    /**
     * 服务主体。
     */
    SERVICE("SERVICE", "服务主体");

    /**
     * 类型代码。
     */
    private final String code;
    /**
     * 类型说明。
     */
    private final String description;

    ApplicationAuthorizationSubjectType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取主体类型。
     *
     * @param code 类型代码
     * @return 主体类型；不存在时返回 null
     */
    public static ApplicationAuthorizationSubjectType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ApplicationAuthorizationSubjectType subjectType : values()) {
            if (subjectType.code.equals(code)) {
                return subjectType;
            }
        }
        return null;
    }

    /**
     * 判断主体类型代码是否有效。
     *
     * @param code 类型代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部主体类型代码。
     *
     * @return 主体类型代码数组
     */
    public static String[] getAllCodes() {
        ApplicationAuthorizationSubjectType[] subjectTypes = values();
        String[] codes = new String[subjectTypes.length];
        for (int index = 0; index < subjectTypes.length; index++) {
            codes[index] = subjectTypes[index].code;
        }
        return codes;
    }

    /**
     * 返回主体类型代码。
     *
     * @return 主体类型代码
     */
    @Override
    public String toString() {
        return code;
    }
}
