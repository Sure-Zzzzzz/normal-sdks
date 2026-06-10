package io.github.surezzzzzz.sdk.oss.s3.support;

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

    public static final String EFFECT_ALLOW = "Allow";
    public static final String EFFECT_DENY = "Deny";
    public static final String SESSION_NAME_TEMPLATE = "%s-session";
    public static final String RESOURCE_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s/*";
    public static final String BUCKET_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s";
    public static final String POLICY_VERSION = "2012-10-17";

    private static final String ACTION_PUT_OBJECT = "s3:PutObject";
    private static final String ACTION_GET_OBJECT = "s3:GetObject";
    private static final String ACTION_ABORT_MULTIPART_UPLOAD = "s3:AbortMultipartUpload";
    private static final String ACTION_LIST_BUCKET_MULTIPART_UPLOADS = "s3:ListBucketMultipartUploads";
    private static final String ACTION_LIST_MULTIPART_UPLOAD_PARTS = "s3:ListMultipartUploadParts";

    private final String version = POLICY_VERSION;
    private Collection<Statement> statement;

    @Getter
    @Setter
    @NoArgsConstructor
    @SuperBuilder
    public static class Statement {

        private final String effect = EFFECT_DENY;
        private final Collection<String> action = Arrays.asList(
                ACTION_PUT_OBJECT,
                ACTION_GET_OBJECT,
                ACTION_ABORT_MULTIPART_UPLOAD,
                ACTION_LIST_BUCKET_MULTIPART_UPLOADS,
                ACTION_LIST_MULTIPART_UPLOAD_PARTS);
        private Collection<String> notResource;
    }
}