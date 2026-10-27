package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评价结果响应。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResponse {

    /**
     * 已评价标记
     */
    private boolean evaluated;

    /**
     * 星级
     */
    private int star;
}
