package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对外安全错误响应，不暴露领域错误码或异常细节。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketErrorResponse {

    /** 面向调用方的安全消息。 */
    private String message;

    /** 服务端生成时间。 */
    private String time;
}
