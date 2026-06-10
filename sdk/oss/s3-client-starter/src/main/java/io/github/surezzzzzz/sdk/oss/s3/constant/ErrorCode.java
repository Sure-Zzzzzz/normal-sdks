package io.github.surezzzzzz.sdk.oss.s3.constant;

/**
 * S3Client 错误码
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 客户端错误（1xx）====================
    public static final String OBJECT_NOT_EXIST = "OSS_101";
    public static final String FILE_NOT_FOUND = "OSS_102";
    public static final String OBJECT_ALREADY_EXISTS = "OSS_103";

    // ==================== 服务端错误（2xx）====================
    public static final String CREATE_BUCKET_FAILED = "OSS_201";
    public static final String CREATE_FOLDER_FAILED = "OSS_202";
    public static final String UPLOAD_OBJECT_FAILED = "OSS_203";
    public static final String DOWNLOAD_OBJECT_FAILED = "OSS_204";
    public static final String DELETE_OBJECT_FAILED = "OSS_205";
    public static final String COPY_OBJECT_FAILED = "OSS_206";
    public static final String LIST_OBJECTS_FAILED = "OSS_207";
    public static final String GET_OBJECT_METADATA_FAILED = "OSS_208";
}