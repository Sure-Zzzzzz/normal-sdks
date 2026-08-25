package io.github.surezzzzzz.sdk.prometheus.client.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 查询结果数据封装。
 *
 * @author surezzzzzz
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Data {

    /**
     * 结果类型（1.0.0 支持 vector / matrix）。
     */
    private String resultType;

    /**
     * 时间序列列表。
     */
    private List<Metric> result;
}
