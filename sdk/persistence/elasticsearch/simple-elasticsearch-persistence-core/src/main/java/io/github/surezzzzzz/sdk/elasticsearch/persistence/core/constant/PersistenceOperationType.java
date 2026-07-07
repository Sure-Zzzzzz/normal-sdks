package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant;

import lombok.Getter;

/**
 * Persistence Operation Type Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum PersistenceOperationType {

    INDEX("index", "索引写入"),
    CREATE("create", "仅新增写入"),
    UPDATE("update", "局部更新"),
    DELETE("delete", "按 ID 删除"),
    BULK("bulk", "批量写入"),
    UPDATE_BY_QUERY("update_by_query", "按查询更新"),
    DELETE_BY_QUERY("delete_by_query", "按查询删除"),
    GET_TASK("get_task", "查询任务");

    private final String code;
    private final String description;

    PersistenceOperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PersistenceOperationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PersistenceOperationType type : values()) {
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
        PersistenceOperationType[] types = values();
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
