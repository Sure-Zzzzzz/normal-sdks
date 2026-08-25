package io.github.surezzzzzz.sdk.prometheus.client.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 单条时间序列。
 *
 * @author surezzzzzz
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Metric {

    /**
     * 标签集合。
     */
    private Map<String, String> metric;

    /**
     * 即时查询单个样本 [timestamp, value]。
     */
    private List<Double> value;

    /**
     * 范围查询样本数组 [[t1,v1], [t2,v2], ...]。
     */
    private List<List<Double>> values;
}
