package io.github.surezzzzzz.sdk.license.server.constant;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;

/**
 * 签发/协作任务状态机：PENDING_SIGN → SIGNING → READY。
 *
 * <p>受理期可确定为 REJECTED；可能已调 KMS 但结果不明为 UNCERTAIN（看门狗标记，不自动重签）；
 * 安全放弃为 ABANDONED；READY 永不退回可重签。状态迁移由 rowVersion+executionGeneration 条件更新。</p>
 *
 * @author surezzzzzz
 */
public enum OperationState {

    PENDING_SIGN("PENDING_SIGN", "待签名"),
    SIGNING("SIGNING", "签名中"),
    READY("READY", "已就绪"),
    UNCERTAIN("UNCERTAIN", "结果不明"),
    REJECTED("REJECTED", "已拒绝"),
    ABANDONED("ABANDONED", "已放弃");

    private final String code;
    private final String description;

    OperationState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static OperationState fromCode(String code) {
        for (OperationState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(ErrorMessage.PROTOCOL_FIELD_INVALID);
    }

    public static boolean isValid(String code) {
        for (OperationState value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static String[] getAllCodes() {
        OperationState[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].code;
        }
        return codes;
    }

    /** 是否允许从当前状态迁移到目标状态（状态机唯一合法迁移表）。 */
    public boolean canTransitionTo(OperationState target) {
        if (this == target) {
            return false;
        }
        switch (this) {
            case PENDING_SIGN:
                return target == SIGNING || target == REJECTED;
            case SIGNING:
                return target == READY || target == UNCERTAIN || target == REJECTED;
            case UNCERTAIN:
                // 受控恢复回到 SIGNING（同 credentialId/输入/档案，代数已递增）
                return target == SIGNING || target == ABANDONED;
            case READY:
                return false;
            case REJECTED:
                return false;
            case ABANDONED:
                return false;
            default:
                return false;
        }
    }
}
