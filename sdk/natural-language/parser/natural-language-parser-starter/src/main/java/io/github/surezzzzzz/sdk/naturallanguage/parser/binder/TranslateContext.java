package io.github.surezzzzzz.sdk.naturallanguage.parser.binder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 转换上下文
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslateContext {

    /**
     * 数据源标识（索引名、表名、集合名）
     */
    private String dataSource;

    /**
     * 字段绑定器
     */
    private FieldBinder fieldBinder;

    /**
     * 扩展参数
     */
    private Map<String, Object> extra;

    /**
     * 添加参数
     *
     * @param key   键
     * @param value 值
     */
    public void addParameter(String key, Object value) {
        if (extra == null) {
            extra = new HashMap<>();
        }
        extra.put(key, value);
    }

    /**
     * 获取参数
     *
     * @param key 键
     * @param <T> 值类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        if (extra == null) {
            return null;
        }
        return (T) extra.get(key);
    }

    /**
     * 获取参数（带默认值）
     *
     * @param key          键
     * @param defaultValue 默认值
     * @param <T>          值类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        if (extra == null) {
            return defaultValue;
        }
        Object value = extra.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
