package io.github.surezzzzzz.sdk.prometheus.api.model.request;

import lombok.*;

import java.time.Instant;

/**
 * 范围查询参数
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRangeRequest {
    /**
     * PromQL 表达式
     */
    private String query;
    /**
     * 起始时间（包含）
     */
    private Instant start;
    /**
     * 结束时间（包含）
     */
    private Instant end;
    /**
     * 步长，单位秒
     */
    private Integer step;
}