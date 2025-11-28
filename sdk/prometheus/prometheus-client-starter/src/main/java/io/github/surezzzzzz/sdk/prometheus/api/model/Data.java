package io.github.surezzzzzz.sdk.prometheus.api.model;

import lombok.*;

import java.util.List;

/**
 * 查询结果数据封装
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Data {
    /**
     * 结果类型（瞬时 / 矩阵 / 向量 / 标量 / 字符串）
     */
    private String resultType;
    /**
     * 时间序列列表
     */
    private List<Metric> result;
}