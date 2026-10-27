package io.github.surezzzzzz.sdk.feedback.client.model;

import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;

/**
 * multipart 附件值对象；字节由调用方负责提供，客户端不写本地文件。
 *
 * @author surezzzzzz
 */
public final class FeedbackAttachmentPart {

    private final String filename;
    private final String contentType;
    private final byte[] content;

    public FeedbackAttachmentPart(String filename, String contentType, byte[] content) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new FeedbackClientValidationException("filename");
        }
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new FeedbackClientValidationException("contentType");
        }
        if (content == null) {
            throw new FeedbackClientValidationException("content");
        }
        this.filename = filename;
        this.contentType = contentType;
        this.content = content.clone();
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content.clone();
    }
}
