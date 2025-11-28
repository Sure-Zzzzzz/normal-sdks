package io.github.surezzzzzz.sdk.s3.schema;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/6/12 9:23
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PolicyDocument {

    public static final String EFFECT_ALLOW = "Allow";
    public static final String EFFECT_DENY = "Deny";
    public static final String SESSION_NAME_TEMPLATE = "%s-session";
    public static final String RESOURCE_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s/*";
    public static final String BUCKET_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s";
    //一个policy中，始终都会有一个Version和Statement声明，Version一般都是写2012-10-17，目前都是写个,还有就是很早期的2008-10-17，已经很少见了
    private final String version = "2012-10-17";
    private Collection<Statement> statement;

    @Getter
    @Setter
    @NoArgsConstructor
    @SuperBuilder
    public static class Statement {

        private final String effect = EFFECT_DENY;
        private final Collection<String> action = Arrays.asList("s3:PutObject", "s3:GetObject", "s3:AbortMultipartUpload", "s3:ListBucketMultipartUploads", "s3:ListMultipartUploadParts");
        private Collection<String> notResource;
    }
}
