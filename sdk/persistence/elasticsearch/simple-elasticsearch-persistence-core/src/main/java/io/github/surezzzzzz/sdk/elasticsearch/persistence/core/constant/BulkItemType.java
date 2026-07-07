package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant;

import lombok.Getter;

/**
 * Bulk Item Type Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum BulkItemType {

    INDEX("index", "索引写入"),
    CREATE("create", "仅新增写入"),
    UPDATE("update", "局部更新"),
    DELETE("delete", "按 ID 删除");

    private final String code;
    private final String description;

    BulkItemType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static BulkItemType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (BulkItemType type : values()) {
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
        BulkItemType[] types = values();
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
