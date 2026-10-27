package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 补充请求（PENDING_INFO 时提单人提交）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyRequest {

    /**
     * 补充正文（落留言）
     */
    private String body;
}
