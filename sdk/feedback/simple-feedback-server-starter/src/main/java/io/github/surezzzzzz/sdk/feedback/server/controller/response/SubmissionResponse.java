package io.github.surezzzzzz.sdk.feedback.server.controller.response;

import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 反馈响应 DTO（mine 形态：含 reply；管理形态：含处理留痕全集）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    /**
     * 反馈 ID
     */
    private String id;

    /**
     * 目标应用编码
     */
    private String applicationCode;

    /**
     * 发起形态 code
     */
    private String kind;

    /**
     * 反馈类型 code
     */
    private String type;

    /**
     * 满意度打分
     */
    private int score;

    /**
     * 反馈文本（展示侧转义纯文本）
     */
    private String content;

    /**
     * 联系邮箱（可空）
     */
    private String contactEmail;

    /**
     * 提交人显示名（可空，空时展示回退 subjectId）
     */
    private String submitterDisplayName;

    /**
     * 提交人 subjectId（仅管理形态返回；mine 形态恒本人）
     */
    private String submitterSubjectId;

    /**
     * 页面上下文（可空）
     */
    private String contextPath;

    /**
     * 状态 code
     */
    private String status;

    /**
     * 处理备注（仅管理形态）
     */
    private String handlerNote;

    /**
     * 给提交人的答复（mine 与管理均可见）
     */
    private String reply;

    /**
     * 答复时间
     */
    private Instant repliedAt;

    /**
     * 处理时间
     */
    private Instant handledAt;

    /**
     * 行版本
     */
    private int rowVersion;

    /**
     * 附件元数据
     */
    private List<AttachmentMeta> attachments;

    /**
     * 创建时间
     */
    private Instant createdTime;

    /**
     * 从领域模型构造（mine 形态：不暴露处理人与 subjectId）。
     *
     * @param submission  反馈
     * @param attachments 附件元数据
     * @return mine 响应
     */
    public static SubmissionResponse fromMine(FeedbackSubmission submission,
                                              List<io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment>
                                                      attachments) {
        return base(submission, attachments);
    }

    /**
     * 从领域模型构造（管理形态：全字段）。
     *
     * @param submission  反馈
     * @param attachments 附件元数据
     * @return 管理响应
     */
    public static SubmissionResponse fromAdmin(FeedbackSubmission submission,
                                               List<io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment>
                                                       attachments) {
        SubmissionResponse response = base(submission, attachments);
        response.submitterSubjectId = submission.getSubmitterSubjectId();
        response.handlerNote = submission.getHandlerNote();
        return response;
    }

    private static SubmissionResponse base(
            FeedbackSubmission submission,
            List<io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment> attachments) {
        List<AttachmentMeta> metas = new ArrayList<AttachmentMeta>();
        if (attachments != null) {
            for (io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment attachment : attachments) {
                metas.add(AttachmentMeta.builder()
                        .id(attachment.getId())
                        .originalFilename(attachment.getOriginalFilename())
                        .contentType(attachment.getContentType())
                        .sizeBytes(attachment.getSizeBytes())
                        .build());
            }
        }
        SubmissionResponse response = new SubmissionResponse();
        response.id = submission.getId();
        response.applicationCode = submission.getApplicationCode();
        response.kind = submission.getKind();
        response.type = submission.getType();
        response.score = submission.getScore();
        response.content = submission.getContent();
        response.contactEmail = submission.getContactEmail();
        response.submitterDisplayName = submission.getSubmitterDisplayName();
        response.contextPath = submission.getContextPath();
        response.status = submission.getStatus();
        response.reply = submission.getReply();
        response.repliedAt = submission.getRepliedAt();
        response.handledAt = submission.getHandledAt();
        response.rowVersion = submission.getRowVersion();
        response.attachments = metas;
        response.createdTime = submission.getCreatedTime();
        return response;
    }

    /**
     * 附件元数据 DTO。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentMeta {

        /**
         * 附件 ID
         */
        private String id;

        /**
         * 原始文件名
         */
        private String originalFilename;

        /**
         * 媒体类型
         */
        private String contentType;

        /**
         * 大小（字节）
         */
        private long sizeBytes;
    }
}
