package io.github.surezzzzzz.sdk.prometheus.api.model.response;

import io.github.surezzzzzz.sdk.prometheus.api.model.Data;
import lombok.*;

/**
 * 范围查询响应
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueryRangeResponse {
    /**
     * 状态，成功 "success"
     */
    private String status;
    /**
     * 查询结果数据
     */
    private Data data;
}