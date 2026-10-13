package io.github.surezzzzzz.sdk.feedback.server.model;

import lombok.Builder;
import lombok.Value;

/**
 * 提交人可访问应用（SPI 可访问应用集的条目形态，修正㉓）。
 *
 * <p>聚形态实现按 IAM「页面准入详情」口径填充（displayName/description 与门户侧边栏同源）；
 * 降级/回退路径由注册表行填充。展示名与描述为展示增强字段，可空——消费方须以编码为锚。</p>
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class AccessibleApplication {

    /**
     * 应用编码（与注册表/门户可信应用一致）。
     */
    String applicationCode;

    /**
     * 展示名（可空=来源无名称数据，消费方回退编码展示）。
     */
    String displayName;

    /**
     * 应用描述（可空）。
     */
    String description;
}
