package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import lombok.Getter;

/**
 * IAM 权限类型枚举
 *
 * <p>区分页面权限、接口权限、数据权限三类。存储值为英文枚举 code，
 * 前端展示时再翻译为中文标签。
 *
 * @author surezzzzzz
 */
@Getter
public enum PermissionType {

    /**
     * 页面权限：控制菜单 / 页面是否可见
     */
    PAGE("page", "页面权限"),

    /**
     * 接口权限：控制后端 API 是否可调用
     */
    API("api", "接口权限"),

    /**
     * 数据权限：控制数据范围（行 / 列）
     */
    DATA("data", "数据权限");

    private final String code;
    private final String description;

    PermissionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static PermissionType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PermissionType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
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
        PermissionType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    /**
     * 返回存储码值（库表 / 协议侧统一使用 code，展示文案由前端负责）
     */
    @Override
    public String toString() {
        return code;
    }
}
