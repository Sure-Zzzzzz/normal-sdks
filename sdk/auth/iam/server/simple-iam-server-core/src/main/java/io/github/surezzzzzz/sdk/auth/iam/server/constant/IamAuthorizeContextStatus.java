package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import lombok.Getter;

/**
 * IAM 授权上下文状态枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum IamAuthorizeContextStatus {

    /**
     * 等待用户登录
     */
    PENDING_LOGIN("PENDING_LOGIN", "等待登录"),

    /**
     * 等待用户确认授权范围
     */
    PENDING_CONSENT("PENDING_CONSENT", "等待授权确认"),

    /**
     * 已批准，等待 SAS 完成授权码签发
     */
    APPROVED("APPROVED", "已批准"),

    /**
     * 用户拒绝授权
     */
    DENIED("DENIED", "已拒绝"),

    /**
     * SAS 已完成授权码签发
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 已超时失效
     */
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String description;

    IamAuthorizeContextStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 状态代码
     * @return 状态枚举，不存在时返回 null
     */
    public static IamAuthorizeContextStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (IamAuthorizeContextStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断状态代码是否有效
     *
     * @param code 状态代码
     * @return true 表示有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部状态代码
     *
     * @return 状态代码数组
     */
    public static String[] getAllCodes() {
        IamAuthorizeContextStatus[] statuses = values();
        String[] codes = new String[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            codes[i] = statuses[i].code;
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
