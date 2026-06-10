package io.github.surezzzzzz.sdk.oss.s3.constant;

/**
 * S3Client 错误消息
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String OBJECT_NOT_EXIST = "对象不存在";
    public static final String FILE_NOT_FOUND = "文件不存在：%s";
    public static final String OBJECT_ALREADY_EXISTS = "对象已存在";
    public static final String CREATE_BUCKET_FAILED = "创建存储桶失败：%s";
    public static final String CREATE_FOLDER_FAILED = "创建文件夹失败：%s";
    public static final String UPLOAD_OBJECT_FAILED = "上传对象失败：%s";
    public static final String DOWNLOAD_OBJECT_FAILED = "下载对象失败：%s";
    public static final String DELETE_OBJECT_FAILED = "删除对象失败：%s";
    public static final String COPY_OBJECT_FAILED = "复制对象失败：%s";
    public static final String LIST_OBJECTS_FAILED = "列举对象失败：%s";
    public static final String GET_OBJECT_METADATA_FAILED = "获取对象元信息失败：%s";

    public static final String BUCKET_EXISTS_BUT_NOT_FOUND = "存储桶已存在，但无法获取存储桶信息";
}