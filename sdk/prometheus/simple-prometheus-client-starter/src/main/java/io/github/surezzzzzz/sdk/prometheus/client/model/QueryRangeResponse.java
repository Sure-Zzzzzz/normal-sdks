package io.github.surezzzzzz.sdk.prometheus.client.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 范围查询响应。
 *
 * @author surezzzzzz
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueryRangeResponse {

    /**
     * 状态，成功为 "success"。
     */
    private String status;

    /**
     * 查询结果数据。
     */
    private Data data;
}
