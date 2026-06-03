package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;

import java.util.List;

/**
 * Boolean Helper - 布尔值判断工具类
 *
 * <p>判断值是否为真，用于条件块渲染决策。
 *
 * <p>真值规则：
 * <ul>
 *   <li>{@code null} → false</li>
 *   <li>{@link Boolean} → 取值</li>
 *   <li>{@link String} → 非空为 true</li>
 *   <li>{@link List} → 非空为 true</li>
 *   <li>其他类型 → true（对象存在即为真）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
public class BooleanHelper {

    /**
     * 判断值是否为真（用于条件块判断）
     *
     * @param value 待判断的值
     * @return 真值返回 true，假值返回 false
     */
    public boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        if (value instanceof List) {
            return !((List<?>) value).isEmpty();
        }
        return true;
    }
}