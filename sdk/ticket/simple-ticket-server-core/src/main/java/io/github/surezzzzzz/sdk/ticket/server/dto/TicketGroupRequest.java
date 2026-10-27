package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理面处理组写请求。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroupRequest {

    /** 处理组 ID；为空表示创建。 */
    private Long id;

    /** 组编码。 */
    private String code;

    /** 组名称。 */
    private String name;

    /** 组员主体标识。 */
    @Builder.Default
    private List<String> memberSubjectIds = new ArrayList<>();

    /** 组长主体标识。 */
    private String leaderSubjectId;

    /** 是否启用。 */
    private boolean enabled;
}
