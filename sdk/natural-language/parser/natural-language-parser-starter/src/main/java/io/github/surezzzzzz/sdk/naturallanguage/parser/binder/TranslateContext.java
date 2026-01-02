package io.github.surezzzzzz.sdk.naturallanguage.parser.binder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 转换上下文
 * <p>
 * 包含转换过程中需要的上下文信息
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
     * 额外参数（扩展用）
     */
    private Map<String, Object> parameters;

    /**
     * 添加参数
     */
    public void addParameter(String key, Object value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
    }

    /**
     * 获取参数
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key) {
        if (parameters == null) {
            return null;
        }
        return (T) parameters.get(key);
    }

    /**
     * 获取参数（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        if (parameters == null) {
            return defaultValue;
        }
        Object value = parameters.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
