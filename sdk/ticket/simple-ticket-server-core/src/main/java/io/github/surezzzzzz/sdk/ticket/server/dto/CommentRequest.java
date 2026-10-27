package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 留言请求。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {

    /**
     * 正文 Markdown
     */
    private String body;

    /**
     * 内部备注（仅处理侧可 true）
     */
    private Boolean internal;
}
