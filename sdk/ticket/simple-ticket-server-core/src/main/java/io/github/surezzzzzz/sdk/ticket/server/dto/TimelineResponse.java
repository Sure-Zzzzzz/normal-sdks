package io.github.surezzzzzz.sdk.ticket.server.dto;

import io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketComment;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketTransition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间线响应（转移+留言升序合并展示；internal 已按角色过滤；附件随行）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineResponse {

    /**
     * 转移记录（升序）
     */
    @Builder.Default
    private List<TicketTransition> transitions = new ArrayList<>();

    /**
     * 留言（升序，已过滤 internal）
     */
    @Builder.Default
    private List<TicketComment> comments = new ArrayList<>();

    /**
     * 附件
     */
    @Builder.Default
    private List<TicketAttachment> attachments = new ArrayList<>();

    /** 下一页游标；没有更早记录时为 null。 */
    private String nextCursor;
}
