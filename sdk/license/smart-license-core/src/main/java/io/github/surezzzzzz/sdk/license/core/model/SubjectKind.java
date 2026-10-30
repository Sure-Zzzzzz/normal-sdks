package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * 授权对象种类：部署、人员、服务。
 *
 * <p>与校验模式相互独立； Grant 固定一种对象种类，不原地更换。</p>
 *
 * @author surezzzzzz
 */
public enum SubjectKind {

    /** 部署：装在客户环境的一套系统，必须经激活绑定。 */
    DEPLOYMENT("DEPLOYMENT", "部署"),

    /** 人员：客户组织中的具体人。 */
    PERSON("PERSON", "人员"),

    /** 服务：机器账号或后端系统。 */
    SERVICE("SERVICE", "服务");

    private final String code;
    private final String description;

    SubjectKind(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 按编码解析；未知编码抛出确定异常，不做 null 宽容。 */
    public static SubjectKind fromCode(String code) {
        for (SubjectKind value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(ErrorMessage.SUBJECT_KIND_INVALID);
    }

    public static boolean isValid(String code) {
        for (SubjectKind value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getAllCodes() {
        SubjectKind[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }

    /** 是否为人员或服务（具备真实身份引用的种类）。 */
    public boolean isSubjectRefKind() {
        return this == PERSON || this == SERVICE;
    }
}
