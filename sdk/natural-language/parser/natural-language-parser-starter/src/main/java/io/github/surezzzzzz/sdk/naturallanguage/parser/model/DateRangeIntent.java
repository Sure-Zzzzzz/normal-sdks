package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间范围意图
 * <p>
 * 表示一个时间范围查询条件，通常用于过滤数据。
 * <p>
 * 示例：
 * - "时间范围2025-01-01到2026-01-01"
 * - "从2025-01-01至2026-01-01"
 * - "2025-01-01到2026-01-01之间"
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateRangeIntent {

    /**
     * 时间字段提示（如"创建时间"、"更新时间"、"timestamp"）
     * 可能为null，表示没有明确指定字段
     */
    private String fieldHint;

    /**
     * 开始时间（ISO 8601格式）
     * 示例："2025-01-01T00:00:00"
     */
    private String from;

    /**
     * 结束时间（ISO 8601格式）
     * 示例："2026-01-01T00:00:00"
     */
    private String to;

    /**
     * 是否包含开始时间（默认true）
     */
    @Builder.Default
    private boolean includeFrom = true;

    /**
     * 是否包含结束时间（默认true）
     */
    @Builder.Default
    private boolean includeTo = true;

    /**
     * 便捷方法：是否有效
     */
    public boolean isValid() {
        return from != null || to != null;
    }
}
