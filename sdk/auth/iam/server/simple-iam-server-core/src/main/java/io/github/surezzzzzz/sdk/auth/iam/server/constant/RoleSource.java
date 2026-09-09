package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import lombok.Getter;

/**
 * 用户角色 / 权限来源枚举
 *
 * <p>区分个人直接授予与直属部门挂载继承两种来源，用户同时持有两种来源时以个人直接为准。
 *
 * @author surezzzzzz
 */
@Getter
public enum RoleSource {

    /**
     * 个人直接授予
     */
    DIRECT("direct", "个人直接"),

    /**
     * 由直属部门挂载继承
     */
    DEPARTMENT_INHERITED("department_inherited", "部门继承");

    private final String code;
    private final String description;

    RoleSource(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 来源代码
     * @return 枚举，如果不存在返回 null
     */
    public static RoleSource fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RoleSource source : values()) {
            if (source.code.equalsIgnoreCase(code)) {
                return source;
            }
        }
        return null;
    }

    /**
     * 判断来源代码是否有效
     *
     * @param code 来源代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 返回存储码值（协议侧统一使用 code，展示文案由前端负责）
     */
    @Override
    public String toString() {
        return code;
    }
}
