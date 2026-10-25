package io.github.surezzzzzz.sdk.feedback.server.model;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import io.github.surezzzzzz.sdk.feedback.server.exception.AttachmentRejectException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 反馈附件聚合（feedback_attachment）+ 上传件校验规则。
 *
 * <p>objectKey 由服务端生成（feedback/{submissionId}/{uuid}.{ext}），不接受调用方指定；
 * 下载为服务端代理流式输出，不做预签名 URL。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackAttachment {

    /**
     * 附件 ID
     */
    private String id;

    /**
     * 所属反馈 ID
     */
    private String submissionId;

    /**
     * 对象存储键（服务端生成）
     */
    private String storageKey;

    /**
     * 用户侧原始文件名（仅作下载 Content-Disposition 展示）
     */
    private String originalFilename;

    /**
     * 媒体类型（白名单内）
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private long sizeBytes;

    /**
     * 创建时间（UTC 可逆）
     */
    private Instant createdTime;

    /**
     * 校验上传件集合（数量/单文件大小/总大小/媒体类型白名单）。
     *
     * @param specs 上传件规格集合（可为空集合=无附件）
     * @throws AttachmentRejectException 任一维度越界（错误码区分 400/413/415 语义）
     */
    public static void validateSpecs(List<UploadSpec> specs) {
        validateSpecs(specs,
                SimpleFeedbackServerConstant.ATTACHMENT_MAX_COUNT,
                SimpleFeedbackServerConstant.ATTACHMENT_MAX_SIZE_BYTES,
                SimpleFeedbackServerConstant.ATTACHMENT_MAX_TOTAL_SIZE_BYTES,
                SimpleFeedbackServerConstant.ATTACHMENT_ALLOWED_CONTENT_TYPES);
    }

    /**
     * 按调用方提供的附件策略校验上传件集合。
     *
     * <p>默认重载保持领域层原有固定策略；starter 可将运行配置传入此重载，避免
     * 配置放宽后又被领域默认值二次拒绝。负大小、空规格和总量累加溢出均视为无效请求。</p>
     *
     * @param specs               上传件规格集合（可为空集合=无附件）
     * @param maxCount            附件数量上限
     * @param maxSizeBytes        单文件大小上限
     * @param maxTotalSizeBytes   总大小上限
     * @param allowedContentTypes 媒体类型白名单
     * @throws AttachmentRejectException 任一维度越界（错误码区分 400/413/415 语义）
     */
    public static void validateSpecs(List<UploadSpec> specs, int maxCount, long maxSizeBytes,
                                     long maxTotalSizeBytes, String[] allowedContentTypes) {
        if (specs == null) {
            return;
        }
        if (specs.size() > maxCount) {
            throw new AttachmentRejectException(ErrorCode.ATTACHMENT_COUNT_EXCEEDED,
                    String.format(ErrorMessage.ATTACHMENT_COUNT_EXCEEDED, maxCount));
        }
        long total = 0L;
        for (UploadSpec spec : specs) {
            if (spec == null || spec.getSizeBytes() < 0) {
                throw new AttachmentRejectException(ErrorCode.INVALID_REQUEST, ErrorMessage.INVALID_REQUEST);
            }
            if (spec.getSizeBytes() > maxSizeBytes) {
                throw new AttachmentRejectException(ErrorCode.ATTACHMENT_SIZE_EXCEEDED,
                        String.format(ErrorMessage.ATTACHMENT_SIZE_EXCEEDED, maxSizeBytes));
            }
            try {
                total = Math.addExact(total, spec.getSizeBytes());
            } catch (ArithmeticException ignored) {
                throw new AttachmentRejectException(ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED,
                        String.format(ErrorMessage.ATTACHMENT_TOTAL_SIZE_EXCEEDED, maxTotalSizeBytes));
            }
            boolean allowed = false;
            for (String candidate : allowedContentTypes == null ? new String[0] : allowedContentTypes) {
                if (candidate != null && candidate.equalsIgnoreCase(spec.getContentType())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new AttachmentRejectException(ErrorCode.ATTACHMENT_MEDIA_TYPE_REJECTED,
                        String.format(ErrorMessage.ATTACHMENT_MEDIA_TYPE_REJECTED,
                                Arrays.toString(allowedContentTypes)));
            }
            if (spec.getFilename() != null
                    && spec.getFilename().length() > SimpleFeedbackServerConstant.ATTACHMENT_FILENAME_MAX_LENGTH) {
                throw new AttachmentRejectException(ErrorCode.FIELD_TOO_LONG,
                        String.format(ErrorMessage.FIELD_TOO_LONG, "filename",
                                SimpleFeedbackServerConstant.ATTACHMENT_FILENAME_MAX_LENGTH));
            }
        }
        if (total > maxTotalSizeBytes) {
            throw new AttachmentRejectException(ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED,
                    String.format(ErrorMessage.ATTACHMENT_TOTAL_SIZE_EXCEEDED,
                            maxTotalSizeBytes));
        }
    }

    /**
     * 上传件规格值对象（提交时校验用：文件名/媒体类型/大小）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadSpec {

        /**
         * 用户侧原始文件名
         */
        private String filename;

        /**
         * 媒体类型
         */
        private String contentType;

        /**
         * 文件大小（字节）
         */
        private long sizeBytes;
    }
}
