package io.github.surezzzzzz.sdk.ticket.server.model;

import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 工单留言（ticket_comment 表）。不可变只增；internal=处理人内部备注，提单人不可见（DESIGN §6.5）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketComment {

    private Long id;

    /**
     * 所属工单 ID
     */
    private Long ticketId;

    /**
     * 作者标识
     */
    private String authorSubjectId;

    /**
     * 作者角色（OWNER/ASSIGNEE/ADMIN/SYSTEM）
     */
    private ActorRole authorRole;

    /**
     * 作者姓名快照
     */
    private String authorName;

    /**
     * 正文（Markdown）
     */
    private String body;

    /**
     * 处理人内部备注（提单人不可见，组内+admin 可见）
     */
    private boolean internal;

    /**
     * 创建时刻
     */
    private Instant createdAt;
}
