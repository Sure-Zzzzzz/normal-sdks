package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant;

import lombok.Getter;

/**
 * Index Operation Type Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum IndexOperationType {

    INDEX("index", "新增或覆盖"),
    CREATE("create", "仅新增");

    private final String code;
    private final String description;

    IndexOperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static IndexOperationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (IndexOperationType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        IndexOperationType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
