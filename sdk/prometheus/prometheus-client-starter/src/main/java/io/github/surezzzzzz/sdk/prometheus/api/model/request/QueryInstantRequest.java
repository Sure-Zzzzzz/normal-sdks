package io.github.surezzzzzz.sdk.prometheus.api.model.request;

import lombok.*;

import java.time.Instant;

/**
 * 瞬时查询参数
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryInstantRequest {
    /**
     * PromQL 表达式
     */
    private String query;
    /**
     * 查询时间点，null 表示当前时间
     */
    private Instant time;
}