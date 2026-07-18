package io.github.surezzzzzz.sdk.auth.data.permission.core.constant;

import lombok.Getter;

/**
 * 数据访问结果。
 *
 * @author surezzzzzz
 */
@Getter
public enum DataAccessOutcome {

    /**
     * 拒绝访问。
     */
    DENY("DENY", "拒绝访问"),
    /**
     * 允许访问全部数据。
     */
    ALLOW_ALL("ALLOW_ALL", "允许访问全部数据"),
    /**
     * 允许访问受限数据。
     */
    ALLOW_RESTRICTED("ALLOW_RESTRICTED", "允许访问受限数据");

    /**
     * 结果代码。
     */
    private final String code;
    /**
     * 结果说明。
     */
    private final String description;

    DataAccessOutcome(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取访问结果。
     *
     * @param code 结果代码
     * @return 访问结果；不存在时返回 null
     */
    public static DataAccessOutcome fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataAccessOutcome outcome : values()) {
            if (outcome.code.equals(code)) {
                return outcome;
            }
        }
        return null;
    }

    /**
     * 判断访问结果代码是否有效。
     *
     * @param code 结果代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部访问结果代码。
     *
     * @return 访问结果代码数组
     */
    public static String[] getAllCodes() {
        DataAccessOutcome[] outcomes = values();
        String[] codes = new String[outcomes.length];
        for (int index = 0; index < outcomes.length; index++) {
            codes[index] = outcomes[index].code;
        }
        return codes;
    }

    /**
     * 返回访问结果代码。
     *
     * @return 访问结果代码
     */
    @Override
    public String toString() {
        return code;
    }
}
