package io.github.surezzzzzz.sdk.ticket.server.constant;

import lombok.Getter;

/**
 * 动作角色。
 *
 * @author surezzzzzz
 */
@Getter
public enum ActorRole {

    /**
     * 提单人
     */
    OWNER("提单人"),

    /**
     * 处理人
     */
    ASSIGNEE("处理人"),

    /**
     * 管理员或组长
     */
    ADMIN("管理员或组长"),

    /**
     * 系统
     */
    SYSTEM("系统");

    /**
     * 与原枚举名称一致的角色代码。
     */
    private final String code;

    /**
     * 角色说明。
     */
    private final String description;

    ActorRole(String description) {
        this.code = name();
        this.description = description;
    }

    /**
     * 按角色代码查找。
     *
     * @param code 角色代码
     * @return 角色，不存在时返回 null
     */
    public static ActorRole fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ActorRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 判断角色代码是否有效。
     *
     * @param code 角色代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部角色代码。
     *
     * @return 角色代码数组
     */
    public static String[] getAllCodes() {
        ActorRole[] roles = values();
        String[] codes = new String[roles.length];
        for (int i = 0; i < roles.length; i++) {
            codes[i] = roles[i].code;
        }
        return codes;
    }

    /**
     * 返回与原枚举字符串表示一致的代码。
     *
     * @return 角色代码
     */
    @Override
    public String toString() {
        return code;
    }
}
