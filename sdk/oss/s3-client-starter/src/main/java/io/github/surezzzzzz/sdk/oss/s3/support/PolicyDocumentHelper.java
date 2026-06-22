package io.github.surezzzzzz.sdk.oss.s3.support;

import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.Collection;

/**
 * IAM Policy Document 辅助类
 * 用于生成临时凭证的权限策略文档
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PolicyDocumentHelper {

    /**
     * IAM 策略效果：允许
     */
    public static final String EFFECT_ALLOW = S3ClientConstant.POLICY_EFFECT_ALLOW;
    /**
     * IAM 策略效果：拒绝
     */
    public static final String EFFECT_DENY = S3ClientConstant.POLICY_EFFECT_DENY;
    /**
     * STS 会话名称模板
     */
    public static final String SESSION_NAME_TEMPLATE = S3ClientConstant.STS_SESSION_NAME_TEMPLATE;
    /**
     * 资源策略 ARN 模板
     */
    public static final String RESOURCE_POLICY_ARN_TEMPLATE = S3ClientConstant.RESOURCE_POLICY_ARN_TEMPLATE;
    /**
     * 存储桶策略 ARN 模板
     */
    public static final String BUCKET_POLICY_ARN_TEMPLATE = S3ClientConstant.BUCKET_POLICY_ARN_TEMPLATE;
    /**
     * IAM 策略版本
     */
    public static final String POLICY_VERSION = S3ClientConstant.POLICY_VERSION;

    /**
     * S3 操作：上传对象
     */
    private static final String ACTION_PUT_OBJECT = "s3:PutObject";
    /**
     * S3 操作：获取对象
     */
    private static final String ACTION_GET_OBJECT = "s3:GetObject";
    /**
     * S3 操作：中止分段上传
     */
    private static final String ACTION_ABORT_MULTIPART_UPLOAD = "s3:AbortMultipartUpload";
    /**
     * S3 操作：列举存储桶分段上传
     */
    private static final String ACTION_LIST_BUCKET_MULTIPART_UPLOADS = "s3:ListBucketMultipartUploads";
    /**
     * S3 操作：列举分段上传分段
     */
    private static final String ACTION_LIST_MULTIPART_UPLOAD_PARTS = "s3:ListMultipartUploadParts";

    /**
     * 策略版本
     */
    private final String version = POLICY_VERSION;

    /**
     * 策略声明列表
     */
    private Collection<Statement> statement;

    /**
     * IAM 策略声明
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @SuperBuilder
    public static class Statement {

        /**
         * 声明效果（默认 Deny）
         */
        private final String effect = EFFECT_DENY;
        /**
         * 允许的 S3 操作列表
         */
        private final Collection<String> action = Arrays.asList(
                ACTION_PUT_OBJECT,
                ACTION_GET_OBJECT,
                ACTION_ABORT_MULTIPART_UPLOAD,
                ACTION_LIST_BUCKET_MULTIPART_UPLOADS,
                ACTION_LIST_MULTIPART_UPLOAD_PARTS);
        /**
         * 排除的资源列表（notResource 策略）
         */
        private Collection<String> notResource;
    }
}