package io.github.surezzzzzz.sdk.naturallanguage.parser.binder;

import java.util.List;

/**
 * 字段绑定器（SPI接口，由具体数据源实现）
 * <p>
 * 用于将用户输入的字段提示（fieldHint）绑定到实际的字段名
 *
 * @author surezzzzzz
 */
public interface FieldBinder {

    /**
     * 绑定字段
     *
     * @param fieldHint 字段提示（用户输入的，可能是中文、拼音、英文）
     * @param dataSource 数据源标识（索引名、表名、集合名等）
     * @return 实际的字段名，如果找不到返回 null
     */
    String bindField(String fieldHint, String dataSource);

    /**
     * 获取数据源的所有可用字段
     *
     * @param dataSource 数据源标识
     * @return 字段列表
     */
    List<String> getAvailableFields(String dataSource);

    /**
     * 获取支持的数据源类型
     *
     * @return 数据源类型（如："elasticsearch", "mysql", "mongodb"）
     */
    default String getDataSourceType() {
        return "unknown";
    }
}
