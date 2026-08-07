package io.github.surezzzzzz.sdk.auth.resource.core.constant;

import lombok.Getter;

/**
 * 已验证资源主体类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum ResourceSubjectType {

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

    ResourceSubjectType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取主体类型。
     *
     * @param code 类型代码
     * @return 主体类型；不存在时返回null
     */
    public static ResourceSubjectType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ResourceSubjectType subjectType : values()) {
            if (subjectType.code.equals(code)) {
                return subjectType;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效。
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
        ResourceSubjectType[] subjectTypes = values();
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
