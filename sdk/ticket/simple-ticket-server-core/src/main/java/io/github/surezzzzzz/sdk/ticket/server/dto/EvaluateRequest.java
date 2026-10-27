package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评价请求（CLOSED 后一次）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateRequest {

    /**
     * 星级 1-5
     */
    private Integer star;

    /**
     * 评语
     */
    private String comment;
}
