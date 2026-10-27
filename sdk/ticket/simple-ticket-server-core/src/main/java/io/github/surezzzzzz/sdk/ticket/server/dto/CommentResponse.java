package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 留言响应。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    /**
     * 留言 ID
     */
    private Long id;

    /**
     * 作者角色
     */
    private String authorRole;

    /**
     * 作者姓名快照
     */
    private String authorName;

    /**
     * 正文
     */
    private String body;

    /**
     * 内部备注（提单人不可见标记）
     */
    private boolean internal;

    /**
     * 创建时刻
     */
    private String createdAt;
}
