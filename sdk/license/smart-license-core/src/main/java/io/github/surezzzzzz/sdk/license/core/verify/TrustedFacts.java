package io.github.surezzzzzz.sdk.license.core.verify;

import io.github.surezzzzzz.sdk.license.core.model.FieldDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本次动作运行事实输入（已由宿主可信适配采集）。
 *
 * <p>类型/范围在求值侧按模型严格校验；未引用事实可省略，引用事实缺失确定拒绝。</p>
 *
 * @author surezzzzzz
 */
public final class TrustedFacts {

    private final Map<String, Object> facts;

    public TrustedFacts(Map<String, Object> facts) {
        this.facts = facts == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(facts));
    }

    public static TrustedFacts none() {
        return new TrustedFacts(null);
    }

    public Map<String, Object> asMap() {
        return facts;
    }

    /** 按模型声明过滤本动作可用事实（未声明字段在此提前拒绝）。 */
    public TrustedFacts restrictTo(Map<String, FieldDefinition> declared) {
        Map<String, Object> filtered = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            if (declared.containsKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return new TrustedFacts(filtered);
    }
}
