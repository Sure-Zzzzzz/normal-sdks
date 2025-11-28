package io.github.surezzzzzz.sdk.prometheus.api.model.response;

import io.github.surezzzzzz.sdk.prometheus.api.model.Data;
import lombok.*;

/**
 * 瞬时查询响应
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueryInstantResponse {
    /**
     * 状态，成功 "success"
     */
    private String status;
    /**
     * 查询结果数据
     */
    private Data data;
}