package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 提交工单请求（portal 面）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    /**
     * 类型编码
     */
    private String typeCode;

    /**
     * 标题
     */
    private String subject;

    /**
     * 描述 Markdown
     */
    private String description;

    /**
     * 优先级（可空默认 NORMAL）
     */
    private String priority;

    /**
     * 模板答案（key→值）
     */
    private Map<String, Object> formAnswers;

    /**
     * 附件（presign 回填：objectKey/fileName/contentType）
     */
    private List<AttachmentItem> attachments;

    /**
     * 关联原单号（重提场景）
     */
    private String relatedTicketNo;

    /**
     * 附件项。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentItem {

        /**
         * 对象键
         */
        private String objectKey;

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 内容类型
         */
        private String contentType;

        /**
         * 声明大小（字节）
         */
        private Long size;
    }
}
