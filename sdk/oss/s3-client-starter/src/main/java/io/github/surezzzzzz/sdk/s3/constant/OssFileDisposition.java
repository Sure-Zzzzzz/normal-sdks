package io.github.surezzzzzz.sdk.s3.constant;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/4/8 10:46
 */
public enum OssFileDisposition {
    DOWNLOAD("attachment"),
    INLINE("inline");

    private final String mode;

    OssFileDisposition(String mode) {
        this.mode = mode;
    }

    public String getContentDisposition(String fileName) {
        return String.format("%s; filename=\"%s\"", mode, fileName);
    }
}
