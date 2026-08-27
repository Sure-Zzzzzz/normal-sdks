package io.github.surezzzzzz.sdk.s3.route.resolver;

import io.github.surezzzzzz.sdk.s3.route.annotation.SimpleS3RouteComponent;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.registry.SimpleS3RouteRegistry;

/**
 * 默认精确 target 解析器。
 *
 * @author surezzzzzz
 */
@SimpleS3RouteComponent
public class DefaultS3RouteResolver implements S3RouteResolver {

    private final SimpleS3RouteRegistry registry;

    public DefaultS3RouteResolver(SimpleS3RouteRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String resolveTargetKey(String targetKey) {
        registry.assertOpen();
        if (targetKey == null || targetKey.trim().isEmpty()) {
            throw new S3RouteException(ErrorCode.TARGET_KEY_ILLEGAL, ErrorMessage.TARGET_KEY_ILLEGAL);
        }
        if (!registry.contains(targetKey)) {
            throw new S3RouteException(ErrorCode.TARGET_NOT_REGISTERED,
                    ErrorMessage.TARGET_NOT_REGISTERED);
        }
        return targetKey;
    }
}
