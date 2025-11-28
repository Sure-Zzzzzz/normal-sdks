package io.github.surezzzzzz.sdk.prometheus.api.model;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 单条时间序列
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Metric {
    /**
     * 标签集合
     */
    private Map<String, String> metric;

    /**
     * 瞬时查询：单个样本 [timestamp, value]
     * 范围查询：样本数组 [[t1,v1], [t2,v2], ...]
     */
    private List<Double> value;      // 瞬时场景
    private List<List<Double>> values; // 范围场景
}