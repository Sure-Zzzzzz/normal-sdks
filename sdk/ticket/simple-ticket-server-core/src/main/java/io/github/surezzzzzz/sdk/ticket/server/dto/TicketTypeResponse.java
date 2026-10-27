package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理面类型响应。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeResponse {

    /** 类型 ID。 */
    private Long id;

    /** 类型编码。 */
    private String code;

    /** 类型名称。 */
    private String name;

    /** 表单模板 JSON。 */
    private String formTemplate;

    /** 默认处理组编码。 */
    private String defaultGroup;

    /** 响应时限分钟。 */
    private Long slaResponseMinutes;

    /** 办结时限分钟。 */
    private Long slaResolutionMinutes;

    /** 是否启用。 */
    private boolean enabled;
}
