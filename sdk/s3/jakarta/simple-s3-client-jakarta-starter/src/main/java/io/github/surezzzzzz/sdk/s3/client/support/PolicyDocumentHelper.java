package io.github.surezzzzzz.sdk.s3.client.support;

import io.github.surezzzzzz.sdk.s3.client.constant.SimpleS3ClientConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.Collection;

/**
 * IAM Policy Document 构造辅助（迁自老 s3-client-starter，语义不变）。
 * 用于生成 STS 临时凭证的降权策略文档（NotResource 限定不可访问资源）。
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PolicyDocumentHelper {

    /**
     * 策略版本
     */
    private final String version = SimpleS3ClientConstant.POLICY_VERSION;

    /**
     * 策略声明列表
     */
    private Collection<Statement> statement;

    /**
     * IAM 策略声明。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @SuperBuilder
    public static class Statement {

        /**
         * 声明效果（固定 Deny）
         */
        private final String effect = SimpleS3ClientConstant.POLICY_EFFECT_DENY;

        /**
         * 策略覆盖的 S3 操作列表（上传/下载/分片治理）
         */
        private final Collection<String> action = Arrays.asList(
                "s3:PutObject",
                "s3:GetObject",
                "s3:AbortMultipartUpload",
                "s3:ListBucketMultipartUploads",
                "s3:ListMultipartUploadParts");

        /**
         * 排除的资源列表（NotResource 语义：声明排除的资源不可操作，其余资源按角色权限放行）
         */
        private Collection<String> notResource;
    }
}
