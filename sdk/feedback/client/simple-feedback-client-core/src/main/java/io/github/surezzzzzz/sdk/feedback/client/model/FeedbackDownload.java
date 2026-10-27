package io.github.surezzzzzz.sdk.feedback.client.model;

/** 附件下载结果，包含服务端媒体类型与文件名。 */
public final class FeedbackDownload {

    private final byte[] content;
    private final String contentType;
    private final String contentDisposition;

    public FeedbackDownload(byte[] content, String contentType, String contentDisposition) {
        this.content = content == null ? new byte[0] : content.clone();
        this.contentType = contentType;
        this.contentDisposition = contentDisposition;
    }

    public byte[] getContent() { return content.clone(); }
    public String getContentType() { return contentType; }
    public String getContentDisposition() { return contentDisposition; }
}
