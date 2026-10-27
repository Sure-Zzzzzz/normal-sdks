package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待补充请求。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestInfoRequest {

    /**
     * 原因码（AWAIT_MATERIAL/AWAIT_THIRDPARTY）
     */
    private String reason;
}
